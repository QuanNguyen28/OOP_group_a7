package com.disasters.comments;

public class CommentCollector {
    public static void main(String[] args){
        YoutubeSearch dataCollector = new YoutubeSearch();
        dataCollector.collectData("yagi typhoon rescue relief",
        "csv_files/yt_comments.csv");
    }
}
