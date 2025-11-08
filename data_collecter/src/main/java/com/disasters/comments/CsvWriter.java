package com.disasters.comments;
/**
 * This class checks if the file specified in the parameter "filePath" exists
 * then creates a new one if not
 * or append data into the already existing one
 */
import java.io.BufferedWriter;
import java.io.File; 
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption; // append data into created file
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter; 

public class CsvWriter {

    public static void write(List<String[]> data, String[] HEADERS, String filePath) {

        // Check if the file already exists
        File file = new File(filePath);
        boolean isNewFile = !file.exists() || file.length() == 0;

        // Create CSVFormat.Builder
        CSVFormat.Builder formatBuilder = CSVFormat.DEFAULT.builder();

        // If file is new or doesnt exist, set the header
        if (isNewFile) {
            formatBuilder.setHeader(HEADERS);
        }

        CSVFormat csvFormat = formatBuilder.build();

        //Open file in APPEND mode and CREATE mode if file doesnt exist
        try (
            BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(filePath),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND,  
                StandardOpenOption.CREATE   
            );

            CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat);
        ) {
            
            // Write data
            for (String[] row : data) {
                csvPrinter.printRecord(row);
            }

            csvPrinter.flush();
            System.out.println("Done!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}