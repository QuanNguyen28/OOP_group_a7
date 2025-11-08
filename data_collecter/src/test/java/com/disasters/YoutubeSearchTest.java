package com.disasters;

import com.disasters.comments.YoutubeSearch;

public class YoutubeSearchTest {
    public static void main(String[] args){
        YoutubeSearch dataCollector = new YoutubeSearch();
        dataCollector.collectData(
            "meme",
            "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\test.csv");
    }
}
