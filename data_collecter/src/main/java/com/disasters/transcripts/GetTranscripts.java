package com.disasters.transcripts;

/**
 * Main application class to run the transcript scraper.
 * This class creates an instance of TranscriptScraper,
 * sets the configuration, and executes the scraping process.
 */
public class GetTranscripts {

    public static void main(String[] args) {
        TranscriptScraper scraper = new TranscriptScraper();

        // Option A: Load the default configuration
        scraper.setConfiguration();

        // Option B: Load a custom configuration
        // String myInput = "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\new_urls.csv";
        // String myOutput = "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\new_transcripts.csv";
        // scraper.setConfiguration(myInput, myOutput);

        // Run the scraper's main logic
        scraper.execute();
    }
}
