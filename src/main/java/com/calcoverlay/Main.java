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

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(Main::fetchAndGenerateImage, 0, 2, TimeUnit.SECONDS);

        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new JFrame("CalcOverlay v1.0.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("Ninjabrainbot Version: N/A | Ping Response: N/A", JLabel.CENTER);
        frame.add(statusLabel, BorderLayout.NORTH);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            System.out.println("Closing application...");
            createEmptyImageBeforeExit();
            frame.dispose();
            System.exit(0);
        });
        frame.add(closeButton, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private static void fetchAndGenerateImage() {
        try {
            String versionUrl = "http://localhost:52533/api/v1/version";
            String versionResponse = sendHttpRequest(versionUrl);
            JSONObject versionJson = new JSONObject(versionResponse);
            String version = versionJson.getString("version");

            String pingUrl = "http://localhost:52533/api/v1/ping";
            String pingResponse = sendHttpRequest(pingUrl);

            updateStatusLabel(version, pingResponse);

            String apiUrl = "http://localhost:52533/api/v1/stronghold";
            String apiResponse = sendHttpRequest(apiUrl);

            JSONObject jsonResponse = new JSONObject(apiResponse);

            JSONObject playerPosition = jsonResponse.optJSONObject("playerPosition");
            boolean isInOverworld = playerPosition != null && playerPosition.optBoolean("isInOverworld", false);
            boolean isInNether = playerPosition != null && playerPosition.optBoolean("isInNether", false);

            JSONArray predictions = jsonResponse.optJSONArray("predictions");

            if (predictions != null && predictions.length() > 0) {
                double certainty = predictions.getJSONObject(0).getDouble("certainty");
                double overworldDistance = predictions.getJSONObject(0).getDouble("overworldDistance");
                int netherDistance = (int) Math.ceil(overworldDistance / 8.0);

                JSONObject firstPrediction = predictions.getJSONObject(0);
                int chunkX = firstPrediction.getInt("chunkX");
                int chunkZ = firstPrediction.getInt("chunkZ");

                int overworldX = (chunkX * 16) + 4;
                int overworldZ = (chunkZ * 16) + 4;

                int netherX = (overworldX >= 0) ? (int) Math.ceil(overworldX / 8.0) : (int) Math.floor(overworldX / 8.0);
                int netherZ = (overworldZ >= 0) ? (int) Math.ceil(overworldZ / 8.0) : (int) Math.floor(overworldZ / 8.0);

                int distanceToUse = 0;
                if (isInOverworld) {
                    distanceToUse = (int) Math.round(overworldDistance);
                } else if (isInNether) {
                    distanceToUse = (int) Math.round(netherDistance);
                }

                if (isInOverworld) {
                    createImage(certainty, overworldX, overworldZ, distanceToUse, netherDistance, true);
                } else if (isInNether) {
                    createImage(certainty, netherX, netherZ, distanceToUse, netherDistance, false);
                }
            } else {
                createImageWithoutText();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sendHttpRequest(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    private static void updateStatusLabel(String version, String pingResponse) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ninjabrainbot Version: " + version + " | Ping Response: " + pingResponse);
        });
    }

    private static Color interpolate(Color c1, Color c2, double t) {
        int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
        return new Color(r, g, b);
    }

    private static Color getGradientColor(double percentage) {
        if (percentage <= 0) return Color.decode("#FF0000"); // Red
        if (percentage <= 25) return interpolate(Color.decode("#FF0000"), Color.decode("#d07910"), percentage / 25.0);
        if (percentage <= 50) return interpolate(Color.decode("#d07910"), Color.decode("#FFFF00"), (percentage - 25.0) / 25.0);
        if (percentage <= 75) return interpolate(Color.decode("#FFFF00"), Color.decode("#00CE29"), (percentage - 50.0) / 25.0);
        return Color.decode("#00CE29"); // Green
    }

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

            double percentage = certainty * 100;
            Color certaintyColor = getGradientColor(percentage);

            String certaintyText = String.format("%.1f%%", percentage);
            String coords = String.format("(%d, %d)", x, z);
            String distanceText = "Dist: " + distance;

            FontMetrics metrics = g2d.getFontMetrics(font);
            int xOffset = width - 10;

            g2d.setColor(certaintyColor);
            g2d.drawString(certaintyText, xOffset - metrics.stringWidth(certaintyText), 30);

            g2d.setColor(Color.WHITE);
            g2d.drawString(coords, xOffset - metrics.stringWidth(coords), 60);

            g2d.setColor(Color.WHITE);
            g2d.drawString(distanceText, xOffset - metrics.stringWidth(distanceText), 90);

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

    private static void createEmptyImageBeforeExit() {
        createImageWithoutText();
    }
}