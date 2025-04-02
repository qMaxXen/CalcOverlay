package com.calcoverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.imageio.ImageIO;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static JFrame frame;
    private static JTextArea textArea;
    private static JLabel statusLabel;

    public static void main(String[] args) {
        System.out.println("Hello, CalcOverlay!");

        // Create a ScheduledExecutorService to run the task every 2 seconds
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(Main::fetchAndGenerateImage, 0, 2, TimeUnit.SECONDS);

        // Set up the GUI
        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        // Create main frame
        frame = new JFrame("CalcOverlay v1.0.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);  // Set the window size to 800x600

        // Set up the layout
        frame.setLayout(new BorderLayout());

        // Text area to display logs and information
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Create a status label at the top for version and ping response
        statusLabel = new JLabel("Ninjabrainbot Version: N/A | Ping Response: N/A", JLabel.CENTER);
        frame.add(statusLabel, BorderLayout.NORTH);

        // Add a "Close" button to stop the program
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            System.out.println("Closing application...");
            createEmptyImageBeforeExit();  // Generate an empty image before exiting
            frame.dispose();  // Close the frame
            System.exit(0); // Exit the program
        });
        frame.add(closeButton, BorderLayout.SOUTH);

        // Show the GUI
        frame.setVisible(true);
    }

    private static void fetchAndGenerateImage() {
        try {
            // Fetch and print the version
            String versionUrl = "http://localhost:52533/api/v1/version";
            String versionResponse = sendHttpRequest(versionUrl);
            JSONObject versionJson = new JSONObject(versionResponse);
            String version = versionJson.getString("version");
            System.out.println("Version: " + version);

            // Fetch and print the ping response
            String pingUrl = "http://localhost:52533/api/v1/ping";
            String pingResponse = sendHttpRequest(pingUrl);
            System.out.println("Ping Response: " + pingResponse);

            // Update the status label in the GUI
            updateStatusLabel(version, pingResponse);

            // URL of the API for predictions
            String apiUrl = "http://localhost:52533/api/v1/stronghold";
            String apiResponse = sendHttpRequest(apiUrl);

            // Convert response to JSON
            JSONObject jsonResponse = new JSONObject(apiResponse);

            // Extract player position and check if in Overworld or Nether
            JSONObject playerPosition = jsonResponse.optJSONObject("playerPosition");
            boolean isInOverworld = playerPosition != null && playerPosition.optBoolean("isInOverworld", false);
            boolean isInNether = playerPosition != null && playerPosition.optBoolean("isInNether", false);

            // Extract "predictions" array
            JSONArray predictions = jsonResponse.optJSONArray("predictions");

            // Get coordinates and certainty for image generation
            if (predictions != null && predictions.length() > 0) {
                double certainty = predictions.getJSONObject(0).getDouble("certainty");
                double overworldDistance = predictions.getJSONObject(0).getDouble("overworldDistance");
                double netherDistance = overworldDistance / 8;

                JSONObject firstPrediction = predictions.getJSONObject(0);
                int chunkX = firstPrediction.getInt("chunkX");
                int chunkZ = firstPrediction.getInt("chunkZ");

                // Calculate Overworld coordinates
                int overworldX = (chunkX * 16) + 4;
                int overworldZ = (chunkZ * 16) + 4;

                // Correct rounding: Always round up
                int netherX = (overworldX >= 0) ? (int) Math.ceil(overworldX / 8.0) : (int) Math.floor(overworldX / 8.0);
                int netherZ = (overworldZ >= 0) ? (int) Math.ceil(overworldZ / 8.0) : (int) Math.floor(overworldZ / 8.0);

                // Check if the player is in the Overworld or Nether and choose coordinates accordingly
                if (isInOverworld) {
                    System.out.println("Overworld World X: " + overworldX);
                    System.out.println("Overworld World Z: " + overworldZ);
                    createImage(certainty, overworldX, overworldZ, (int) Math.round(overworldDistance), (int) Math.round(netherDistance), true);
                } else if (isInNether) {
                    System.out.println("Nether World X: " + netherX);
                    System.out.println("Nether World Z: " + netherZ);
                    createImage(certainty, netherX, netherZ, (int) Math.round(overworldDistance), (int) Math.round(netherDistance), false);
                }
            } else {
                // If predictions are empty, create an empty image (without text)
                createImageWithoutText();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to send HTTP GET request and return response as a string
    private static String sendHttpRequest(String urlString) throws Exception {
        // Create a connection
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");

        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    // Method to update the status label with the server and prediction info
    private static void updateStatusLabel(String version, String pingResponse) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ninjabrainbot Version: " + version + " | Ping Response: " + pingResponse);
        });
    }

    // Method to generate and save the image with text
    private static void createImage(double certainty, int x, int z, int distance, int netherDistance, boolean isOverworld) {
        try {
            int width = 600;
            int height = 200;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0, 0, 0, 0));  
            g2d.fillRect(0, 0, width, height);

            Font font = new Font("Arial", Font.BOLD, 20);
            g2d.setFont(font);
            g2d.setColor(Color.WHITE);

            String certaintyText = String.format("%.1f%%", certainty * 100);
            String coords = String.format("(%d, %d)", x, z);
            String distanceText = "Dist: " + distance;

            FontMetrics metrics = g2d.getFontMetrics(font);
            int xOffset = width - 10;  

            g2d.drawString(certaintyText, xOffset - metrics.stringWidth(certaintyText), 30);
            if (isOverworld) {
                g2d.drawString(coords, xOffset - metrics.stringWidth(coords), 60);
                g2d.drawString(distanceText, xOffset - metrics.stringWidth(distanceText), 90);
            } else {
                g2d.drawString(coords, xOffset - metrics.stringWidth(coords), 60);
                g2d.drawString(distanceText, xOffset - metrics.stringWidth(distanceText), 90);
            }

            File file = new File("image.png");
            ImageIO.write(image, "PNG", file);
            System.out.println("Image has been generated and saved as 'image.png'.");

            g2d.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createImageWithoutText() {
        try {
            int width = 600;
            int height = 200;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setColor(new Color(0, 0, 0, 0));  
            g2d.fillRect(0, 0, width, height);

            File file = new File("image.png");
            ImageIO.write(image, "PNG", file);
            System.out.println("Empty image has been generated and saved as 'image.png'.");

            g2d.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to create an empty image before exiting
    private static void createEmptyImageBeforeExit() {
        createImageWithoutText();  // Generate an empty image before closing
    }
}
