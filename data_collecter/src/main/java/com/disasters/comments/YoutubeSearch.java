package com.disasters.comments;
/**
 * Searches YouTube for videos based on keywords and collects
 * top-level comments from the resulting videos.
 * The collected data is then written to a file with CsvWriter.
 */
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
//Search list libs
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
//Comments collecting libs
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.Comment;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class YoutubeSearch {

    private static final String API_KEY = "AIzaSyBj_jn3BNJr73b3Ops-UF32W-PMfACNxKg";
    private static final String APPLICATION_NAME = "DisasterComments";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public void collectData(String keyword_, String filepath) {
        int videosCount = 0; 
        int commentsCount = 0;
        String keyword = keyword_;
        List<String[]> dataRows = new ArrayList<>();
        try {
            // Build the YouTube service object
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            YouTube youtubeService = new YouTube.Builder(
                httpTransport,
                JSON_FACTORY, 
                null
                )
                .setApplicationName(APPLICATION_NAME)
                .build();

            // Create the API request
            YouTube.Search.List request = youtubeService.search()
                .list(Arrays.asList("snippet")) 
                .setKey(API_KEY)
                .setQ(keyword_)
                .setType(Arrays.asList("video"))
                .setMaxResults(100L); 


            System.out.println("Searching for videos");

            // Execute the request
            SearchListResponse response = request.execute();

            // Process and write the data
            List<SearchResult> items = response.getItems();
            if (items != null) {
                for (SearchResult item : items) {
                    // Get title and video ID
                    videosCount += 1;
                    String title = item.getSnippet().getTitle();
                    String videoId = item.getId().getVideoId();
                    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                    YouTube.CommentThreads.List commentRequest = youtubeService.commentThreads()
                        .list(Arrays.asList("snippet"))
                        .setKey(API_KEY)
                        .setVideoId(videoId)
                        .setTextFormat("plainText")
                        .setMaxResults(200L);
                    try {
                        CommentThreadListResponse commentResponse = commentRequest.execute();

                        List<CommentThread> commentThreads = commentResponse.getItems();
                        // Get each comment info
                        if (commentThreads != null){
                            for (CommentThread commentThread : commentThreads) {
                                commentsCount += 1;
                                Comment comment = commentThread.getSnippet().getTopLevelComment();
                                String author = comment.getSnippet().getAuthorDisplayName();
                                String commentText = comment.getSnippet().getTextDisplay();
                                String likeCount = comment.getSnippet().getLikeCount().toString();
                                DateTime publishedAt = comment.getSnippet().getPublishedAt();
                                String published = publishedAt.toString();

                                String[] newRow = new String[]{keyword, videoId, title, videoUrl, "-", author, commentText, likeCount, published};
                            
                                dataRows.add(newRow);
                            }
                        }
                    } catch (GoogleJsonResponseException e){ // SKIP THE VIDEO IF ITS COMMENTS ARE DISABLED
                        if (e.getDetails().getErrors().get(0).getReason().equals("commentsDisabled")) {
                        System.out.println("SKIPPING VIDEO: " + videoId + " (Comments are disabled)");
                        }
                        else {
                            e.printStackTrace();
                            throw e;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            System.out.println("Collected from " + videosCount +" videos");
            System.out.println("Collected " + commentsCount +" comments");
            String[] HEADERS = new String[]{"keyword", "video_id", "video_title", "video_url","transcript", "author", "comment", "like", "published"};
            CsvWriter.write(dataRows, HEADERS, filepath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}