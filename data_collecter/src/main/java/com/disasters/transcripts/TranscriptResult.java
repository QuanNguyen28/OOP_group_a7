package com.disasters.transcripts;

public class TranscriptResult {
    private String videoUrl;
    private String transcript;

    public TranscriptResult(String videoUrl, String transcript) {
        this.videoUrl = videoUrl;
        this.transcript = transcript;
    }

    // Getters
    public String getVideoUrl() {
        return videoUrl;
    }

    public String getTranscript() {
        return transcript;
    }
}
