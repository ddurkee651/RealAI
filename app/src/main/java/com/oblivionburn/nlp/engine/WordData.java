package com.oblivionburn.nlp.engine;

public class WordData {
    private Integer Frequency;
    private String Word;

    public WordData() {}

    public String getWord() { return this.Word; }
    public void setWord(String str) { this.Word = str; }
    public Integer getFrequency() { return this.Frequency; }
    public void setFrequency(Integer num) { this.Frequency = num; }
}
