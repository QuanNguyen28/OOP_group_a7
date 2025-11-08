package com.disasters.comments;

public class CommentCollector {
    public static void main(String[] args){
        YoutubeSearch dataCollector = new YoutubeSearch();
        dataCollector.collectData("yagi typhoon rescue relief",
        "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\relief.csv");
    }
}
