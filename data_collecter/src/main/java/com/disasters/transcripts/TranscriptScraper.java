package com.disasters.transcripts;
/**
 * Scrapes video transcripts using Selenium by reading URLs from an input CSV file.
 * It fetches the transcript from 'youtubetotranscript.com' and appends each result
 * immediately to an output CSV to prevent data loss. 
 * Includes retry
 * and skip-on-error logic.
 */
// --- Selenium WebDriver: For controlling the browser (Chrome) ---
import io.github.bonigarcia.wdm.WebDriverManager; // Automatically manages browser drivers
import org.openqa.selenium.By;                      // For finding elements (e.g., By.id("transcript"))
import org.openqa.selenium.JavascriptExecutor;  // For running JavaScript (like scrolling)
import org.openqa.selenium.Keys;                   // For sending keyboard keys (like Keys.RETURN)
import org.openqa.selenium.WebDriver;              // The main browser interface
import org.openqa.selenium.WebElement;             // Represents an HTML element
import org.openqa.selenium.chrome.ChromeDriver;    // Specific class for Chrome
import org.openqa.selenium.chrome.ChromeOptions;   // For setting options (like --headless)

// --- Selenium Exceptions: For handling specific browser errors ---
import org.openqa.selenium.WebDriverException;   // A general Selenium error
import org.openqa.selenium.TimeoutException;      // Error when a wait condition fails

// --- Selenium Waits: For intelligently waiting for elements ---
import org.openqa.selenium.support.ui.ExpectedConditions; // Defines conditions to wait for
import org.openqa.selenium.support.ui.WebDriverWait;      // The main class for waiting

// --- Apache Commons CSV: For reading and writing CSV files ---
import org.apache.commons.csv.CSVFormat;  // Defines CSV rules (commas, headers)
import org.apache.commons.csv.CSVParser;  // For reading CSV files
import org.apache.commons.csv.CSVPrinter;  // For writing CSV files
import org.apache.commons.csv.CSVRecord;  // Represents a single row (record) in a CSV

// --- Logging (SLF4J): For professional logging (better than System.out) ---
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// --- Java I/O & NIO: For reading/writing files and paths ---
import java.io.BufferedReader;   // Efficiently reads text from a file
import java.io.BufferedWriter;   // Efficiently writes text to a file
import java.io.File;             // Represents a file on the file system
import java.io.IOException;      // The standard exception for I/O errors
import java.nio.charset.StandardCharsets; // For specifying UTF-8 encoding
import java.nio.file.Files;            // Modern utility class for file operations
import java.nio.file.Paths;            // For creating Path objects
import java.nio.file.StandardOpenOption; // For specifying 'APPEND' and 'CREATE'

// --- Java Core Utilities ---
import java.time.Duration;       // For specifying time (e.g., Duration.ofSeconds(15))
import java.util.ArrayList;      // A resizable list
import java.util.List;           // The interface for lists
import com.disasters.comments.CsvWriter;

public class TranscriptScraper {

    // Use Logger instead of System.out.println
    private static final Logger log = LoggerFactory.getLogger(TranscriptScraper.class);

// ------------------------------------------------------------------------------------
    // --- Configuration ---
    // Configuration fields
    private String INPUT_CSV;
    private String OUTPUT_CSV;
    private String[] CSV_HEADERS;
    private String SCRAPE_URL;
    
    private WebDriver driver;

    public void setConfiguration(){
        this.INPUT_CSV = "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\unique_urls.csv";
        this.OUTPUT_CSV = "D:\\HUST_Code_class\\OOP\\Project\\data\\csv_files\\youtube_transcripts_auto.csv";
        this.CSV_HEADERS = new String[]{"video_url", "transcript"};
        this.SCRAPE_URL = "https://youtubetotranscript.com/";
    }

    public void setConfiguration(String input_csv, String output_csv){
        this.INPUT_CSV = input_csv;
        this.OUTPUT_CSV = output_csv;
        this.CSV_HEADERS = new String[]{"video_url", "transcript"};
        this.SCRAPE_URL = "https://youtubetotranscript.com/";
    }

//-----------------------------------------------------------------------------------

/** *****************************************************************************************************
 * EXECUTE:
 * - Writes immediately after finding a transcript (prevents data loss)
 * - Automatically skips on error
**********************************************************************************************************/
    public void execute() {
        // Read URLs from CSV
        List<String> urls = readUrlsFromCsv(this.INPUT_CSV);
        log.info("{} URLs:", urls.size());

        // Setup the browser
        WebDriverManager.chromedriver().setup();
        WebDriver driver = setupDriver();

        // Loop through each URL
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            log.info("[{}/{}] Running: {}", i + 1, urls.size(), url);

            String transcript = null;
            int retryCount = 0;
            
            // --- RETRY AND SKIP LOGIC ---
            while (transcript == null && retryCount < 3) {
                try {
                    transcript = getTranscript(driver, url);
                    
                    if (transcript == null) {
                        // Error (e.g., Timeout) occurred INSIDE getTranscript
                        log.warn(" getTranscript returned null. Retrying (attempt {})...", retryCount + 1);
                        retryCount++; 
                    }
                    
                } catch (WebDriverException e) {
                    // Error (e.g., session)
                    log.warn("Session expired or WebDriver error. Recreating (attempt {})...", retryCount + 1);
                    try {
                        driver.quit();
                    } catch (Exception ex) {
                        // Ignore cleanup errors
                    }
                    driver = setupDriver();
                    retryCount++; // Increment retryCount
                    
                } catch (Exception e) {
                    // SKIP IF FAILED
                    log.error("Unexpected error processing {}: {}", url, e.getMessage(), e);
                    retryCount = 3; // Mark as failed and force exit from 'while' loop
                }
            }
            // --------------------------------------------------------
            
            if (transcript != null) {
                log.info(" Found transcript ({} items).", transcript.length());
            } else {
                log.warn(" Error: Transcripts Not Found after {} retries. Skipping this URL.", retryCount);
            }
            
            // Write the result (success or null) and continue
            TranscriptResult result = new TranscriptResult(url, transcript);
            appendResultToCsv(result, this.OUTPUT_CSV); // Write immediately to file

            try {
                // Short pause to avoid being rate-limited
                Thread.sleep(3000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Exit 'for' loop if the thread is interrupted
            }
        } // End of 'for' loop, automatically moves to the next URL

        // Close the browser
        driver.quit();
        log.info("Saved all results in {}", this.OUTPUT_CSV);
    }

//******************************** CONFIGURE CHROME OPTIONS *********************************************/
    private static WebDriver setupDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new"); 
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--window-size=1280,1024");
        return new ChromeDriver(options);
    }

//******************************** GET TRANSCRIPT ********************************************************/
    private String getTranscript(WebDriver driver, String videoUrl) {
        try {
            driver.get(this.SCRAPE_URL);

            WebElement inputBox = driver.findElement(By.xpath("//input[@placeholder='Paste YouTube URL here...']"));
            inputBox.clear();
            inputBox.sendKeys(videoUrl);
            inputBox.sendKeys(Keys.RETURN);

            // Button-clicking logic 
            try {
                List<WebElement> buttons = driver.findElements(By.xpath("//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'transcript') or contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'get') or contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'show')]"));
                for (WebElement b : buttons) {
                    try {
                        if (b.isDisplayed() && b.isEnabled()) {
                            b.click();
                            Thread.sleep(500);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }

            // Your robust selector-finding logic (Keep original)
            By[] candidates = new By[]{
                    By.id("transcript"),
                    By.cssSelector(".transcript"),
                    By.cssSelector("#transcript_container"),
                    By.xpath("//div[contains(@class,'transcript')]"),
                    By.cssSelector("pre")
            };

            WebElement container = null;
            for (By sel : candidates) {
                try {
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                    container = wait.until(ExpectedConditions.presenceOfElementLocated(sel));
                    if (container != null) break;
                } catch (TimeoutException te) {
                    // try next selector
                }
            }

            if (container == null) {
                try {
                    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
                    container = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("transcript")));
                } catch (TimeoutException te) {
                    String title = "";
                    String page = "";
                    try {
                        title = driver.getTitle();
                        String src = driver.getPageSource();
                        page = src.substring(0, Math.min(3000, src.length()));
                    } catch (Exception ignored) {
                    }
                    log.error("Transcript element not found for {} after waiting. Page title: {}", videoUrl, title);
                    log.debug("Page source snippet: {}", page);
                    return null;
                }
            }
            // Close ad 
            try {
                WebElement adClose = driver.findElement(By.xpath("//button[contains(.,'×') or contains(.,'close')]"));
                adClose.click();
            } catch (Exception e) {
                log.debug("Did not find ad to close."); // Use debug log
            }

            // Scroll down
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < 3; i++) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1000); // Short pause to load
            }

            // Get text
            List<WebElement> spans = container.findElements(By.tagName("span"));
            List<String> spanTexts = new ArrayList<>();
            for (WebElement span : spans) {
                String text = span.getText().strip();
                if (!text.isEmpty()) {
                    spanTexts.add(text);
                }
            }
            return String.join(" ", spanTexts);

        } catch (Exception e) {
            log.error("Error processing {}: {}", videoUrl, e.getMessage());
            // Rethrow WebDriverException if it occurs, so the main retry logic can catch it
            if (e instanceof WebDriverException) {
                throw (WebDriverException) e;
            }
            return null; // Return null for other errors (e.g., Timeout)
        }
    }

// ********************************Read Urls from Csv and return a list of urls***********************************
    private List<String> readUrlsFromCsv(String filePath) {
        List<String> urls = new ArrayList<>();
        try (
            BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8);
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                    .setSkipHeaderRecord(false)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build())
        ) {
            for (CSVRecord record : csvParser) {
                if (record == null || record.size() == 0) continue;
                String first = record.get(0);
                if (first == null) continue;

                // Remove possible BOM and whitespace
                first = first.replace("\uFEFF", "").trim();

                // Skip common header variants that may appear in the CSV
                String low = first.toLowerCase();
                if (low.equals("video_url") || low.equals("url") || low.contains("video_url") || low.contains("video url")) {
                    continue; // header row or bad first line — skip it
                }

                urls.add(first);
            }
        } catch (IOException e) {
            log.error("Error Reading Input CSV: {}", e.getMessage());
        }
        return urls;
    }

// ********************************Write data to Csv specified by "filePath"****************************

    private void appendResultToCsv(TranscriptResult result, String filePath) {
        // Call the CsvWriter class
        List<String[]> dataRow = new ArrayList<>();
        dataRow.add(new String[]{result.getVideoUrl(), result.getTranscript()});
        CsvWriter.write(dataRow, this.CSV_HEADERS, filePath);
    }
}
