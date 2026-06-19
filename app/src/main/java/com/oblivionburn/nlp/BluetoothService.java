package com.oblivionburn.nlp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class BluetoothService {
    private static final String NAME_INSECURE = "RealAI_BluetoothInsecure";
    private static final String NAME_SECURE = "RealAI_BluetoothSecure";
    public static final int STATE_CONNECTED = 3;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_NONE = 0;
    private static final String TAG = "REALAI_BLUETOOTH";

    private ConnectThread ConnectThread;
    private ConnectedThread ConnectedThread;
    private AcceptThread InsecureAcceptThread;
    private AcceptThread SecureAcceptThread;
    private Handler handle_bluetooth;

    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter Adapter = BluetoothAdapter.getDefaultAdapter();
    private int State = 0;

    public interface Constants {
        public static final String DEVICE_NAME = "device_name";
        public static final int MESSAGE_DEVICE_NAME = 4;
        public static final int MESSAGE_READ = 2;
        public static final int MESSAGE_STATE_CHANGE = 1;
        public static final int MESSAGE_TOAST = 5;
        public static final int MESSAGE_WRITE = 3;
        public static final String TOAST = "toast";
    }

    public BluetoothService(Context context, Handler handler) {
        this.handle_bluetooth = handler;
    }

    public synchronized int getState() {
        return this.State;
    }

    public synchronized void start() {
        Log.d(TAG, "start");
        if (this.ConnectThread != null) {
            this.ConnectThread.cancel();
            this.ConnectThread = null;
        }
        if (this.ConnectedThread != null) {
            this.ConnectedThread.cancel();
            this.ConnectedThread = null;
        }
        if (this.SecureAcceptThread == null) {
            this.SecureAcceptThread = new AcceptThread(true);
            this.SecureAcceptThread.start();
        }
        if (this.InsecureAcceptThread == null) {
            this.InsecureAcceptThread = new AcceptThread(false);
            this.InsecureAcceptThread.start();
        }
    }

    public synchronized void connect(BluetoothDevice bluetoothDevice, boolean secure) {
        Log.d(TAG, "connect to: " + bluetoothDevice);
        if (this.State == STATE_CONNECTING && this.ConnectThread != null) {
            this.ConnectThread.cancel();
            this.ConnectThread = null;
        }
        if (this.ConnectedThread != null) {
            this.ConnectedThread.cancel();
            this.ConnectedThread = null;
        }
        this.ConnectThread = new ConnectThread(bluetoothDevice, secure);
        this.ConnectThread.start();
    }

    public synchronized void connected(BluetoothSocket bluetoothSocket, BluetoothDevice bluetoothDevice, String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);
        if (this.ConnectThread != null) {
            this.ConnectThread.cancel();
            this.ConnectThread = null;
        }
        if (this.ConnectedThread != null) {
            this.ConnectedThread.cancel();
            this.ConnectedThread = null;
        }
        if (this.SecureAcceptThread != null) {
            this.SecureAcceptThread.cancel();
            this.SecureAcceptThread = null;
        }
        if (this.InsecureAcceptThread != null) {
            this.InsecureAcceptThread.cancel();
            this.InsecureAcceptThread = null;
        }
        this.ConnectedThread = new ConnectedThread(bluetoothSocket, socketType);
        this.ConnectedThread.start();

        Message msg = this.handle_bluetooth.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, bluetoothDevice.getName());
        msg.setData(bundle);
        this.handle_bluetooth.sendMessage(msg);
    }

    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (this.ConnectThread != null) {
            this.ConnectThread.cancel();
            this.ConnectThread = null;
        }
        if (this.ConnectedThread != null) {
            this.ConnectedThread.cancel();
            this.ConnectedThread = null;
        }
        if (this.SecureAcceptThread != null) {
            this.SecureAcceptThread.cancel();
            this.SecureAcceptThread = null;
        }
        if (this.InsecureAcceptThread != null) {
            this.InsecureAcceptThread.cancel();
            this.InsecureAcceptThread = null;
        }
        this.State = STATE_NONE;
    }

    public void write(byte[] out) {
        synchronized (this) {
            if (this.State != STATE_CONNECTED) {
                return;
            }
            this.ConnectedThread.write(out);
        }
    }

    private void connectionFailed() {
        Message msg = this.handle_bluetooth.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        this.handle_bluetooth.sendMessage(msg);
        this.State = STATE_NONE;
        start();
    }

    private void connectionLost() {
        Message msg = this.handle_bluetooth.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        this.handle_bluetooth.sendMessage(msg);
        this.State = STATE_NONE;
        start();
    }

    // ================== CORRECTED AcceptThread ==================
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket ServerSocket;
        private String SocketType;

        public AcceptThread(boolean secure) {
            this.SocketType = secure ? "Secure" : "Insecure";
            BluetoothServerSocket tmp = null;
            try {
                if (secure) {
                    tmp = Adapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                } else {
                    tmp = Adapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + this.SocketType + " listen() failed", e);
            }
            this.ServerSocket = tmp;
            State = STATE_LISTEN;
        }

        @Override
        public void run() {
            Log.d(TAG, "BEGIN AcceptThread SocketType:" + SocketType);
            setName("AcceptThread" + SocketType);

            BluetoothSocket socket = null;

            while (State != STATE_CONNECTED) {
                try {
                    socket = ServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + SocketType + " accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (State) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice(), SocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END AcceptThread, socket Type: " + SocketType);
        }

        public void cancel() {
            Log.d(TAG, "Socket Type: " + SocketType + " cancel " + this);
            try {
                ServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + SocketType + " close() of server failed", e);
            }
        }
    }

    // ================== ConnectThread (unchanged, correct) ==================
    private class ConnectThread extends Thread {
        private final BluetoothDevice Device;
        private BluetoothSocket Socket;
        private String SocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            this.Device = device;
            this.SocketType = secure ? "Secure" : "Insecure";
            BluetoothSocket tmp = null;
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + this.SocketType + " create() failed", e);
            }
            this.Socket = tmp;
            State = STATE_CONNECTING;
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN ConnectThread SocketType: " + this.SocketType);
            setName("ConnectThread" + this.SocketType);

            // Always cancel discovery because it will slow down a connection
            Adapter.cancelDiscovery();

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Socket.connect();
            } catch (IOException e) {
                // Fallback: try to use a hidden method (for older devices)
                try {
                    Log.e(TAG, "Socket connect failed, trying fallback...");
                    Socket = (BluetoothSocket) Device.getClass()
                            .getMethod("createRfcommSocket", int.class)
                            .invoke(Device, 2);
                    Socket.connect();
                } catch (Exception ex) {
                    Log.e(TAG, "Fallback connect failed", ex);
                    try {
                        Socket.close();
                    } catch (IOException closeEx) {
                        Log.e(TAG, "Unable to close() " + SocketType + " socket during connection failure", closeEx);
                    }
                    connectionFailed();
                    return;
                }
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                ConnectThread = null;
            }

            // Start the connected thread
            connected(Socket, Device, SocketType);
        }

        public void cancel() {
            try {
                Socket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + SocketType + " socket failed", e);
            }
        }
    }

    // ================== ConnectedThread (unchanged, correct) ==================
    private class ConnectedThread extends Thread {
        private final InputStream InStream;
        private final OutputStream OutStream;
        private final BluetoothSocket Socket;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            this.Socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            this.InStream = tmpIn;
            this.OutStream = tmpOut;
            State = STATE_CONNECTED;
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN ConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (State == STATE_CONNECTED) {
                try {
                    bytes = InStream.read(buffer);
                    handle_bluetooth.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                OutStream.write(buffer);
                handle_bluetooth.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                Socket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
