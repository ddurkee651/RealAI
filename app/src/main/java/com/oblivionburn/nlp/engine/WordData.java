package com.oblivionburn.nlp.engine;

class WordData {
    private Integer Frequency;
    private String Word;

    WordData() {}

    String getWord() { return this.Word; }
    void setWord(String str) { this.Word = str; }
    Integer getFrequency() { return this.Frequency; }
    void setFrequency(Integer num) { this.Frequency = num; }
}
