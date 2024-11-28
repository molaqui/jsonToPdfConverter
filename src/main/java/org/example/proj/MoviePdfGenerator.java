package org.example.proj;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MoviePdfGenerator {

    public static void main(String[] args) {
        try {
            // Chemin du fichier JSON contenant les informations des films
            String jsonFilePath = "src/main/resources/movies.json";

            // Lire le fichier JSON et le convertir en une liste de films
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> movies = mapper.readValue(
                    new File(jsonFilePath),
                    new TypeReference<>() {}
            );

            // Générer un PDF pour chaque film
            for (Map<String, Object> movie : movies) {
                generatePdf(movie);
            }

            System.out.println("Les PDF des films ont été générés avec succès !");
        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du fichier JSON ou de la génération des PDF.");
            e.printStackTrace();
        }
    }

    private static void generatePdf(Map<String, Object> movie) {
        String title = (String) movie.get("title");
        String outputFilePath = "output/" + title.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            float margin = 50; // Marge autour de la page
            float yPosition = 750; // Position verticale de départ
            float lineHeight = 20f; // Hauteur entre les lignes
            float maxLineWidth = page.getMediaBox().getWidth() - 2 * margin; // Largeur maximale
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Titre centré
            drawCenteredText(contentStream, "Title: " + title, PDType1Font.HELVETICA_BOLD, 24, page.getMediaBox().getWidth(), yPosition);
            yPosition -= 50;

            // Image centrée
            if (movie.get("thumbnail") != null) {
                String thumbnailUrl = (String) movie.get("thumbnail");
                try {
                    byte[] imageBytes = new URL(thumbnailUrl).openStream().readAllBytes();
                    PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "thumbnail");
                    float imageWidth = 200; // Largeur de l'image
                    float imageHeight = 300; // Hauteur de l'image
                    contentStream.drawImage(image, (page.getMediaBox().getWidth() - imageWidth) / 2, yPosition - imageHeight, imageWidth, imageHeight);
                    yPosition -= imageHeight + 30; // Ajuster la position après l'image
                } catch (Exception e) {
                    System.err.println("Erreur lors du chargement de l'image pour le film : " + title);
                }
            }

            // Ajouter les informations textuelles
            contentStream.setFont(PDType1Font.HELVETICA, 14);
            String[] details = {
                    "Year: " + movie.get("year"),
                    "Genres: " + movie.get("genres"),
                    "Cast: " + movie.get("cast"),
                    "YouTube Link: " + movie.get("link"),
                    "Description:"
            };

            for (String detail : details) {
                List<String> wrappedLines = wrapText(detail, maxLineWidth, PDType1Font.HELVETICA, 14);
                for (String line : wrappedLines) {
                    if (yPosition < margin) { // Ajouter une nouvelle page si nécessaire
                        contentStream.close();
                        page = new PDPage();
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.setFont(PDType1Font.HELVETICA, 14); // Réinitialiser la police pour la nouvelle page
                        yPosition = 750;
                    }
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText(line);
                    contentStream.endText();
                    yPosition -= lineHeight;
                }
            }

            // Ajouter la description
            String description = (String) movie.get("extract");
            List<String> descriptionLines = wrapText(description, maxLineWidth, PDType1Font.HELVETICA, 14);
            for (String line : descriptionLines) {
                if (yPosition < margin) { // Ajouter une nouvelle page si nécessaire
                    contentStream.close();
                    page = new PDPage();
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(PDType1Font.HELVETICA, 14); // Réinitialiser la police pour la nouvelle page
                    yPosition = 750;
                }
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(line);
                contentStream.endText();
                yPosition -= lineHeight;
            }

            contentStream.close();
            new File("output").mkdirs();
            document.save(outputFilePath);
            System.out.println("PDF généré pour le film : " + title);
        } catch (IOException e) {
            System.err.println("Erreur lors de la génération du PDF pour le film : " + title);
            e.printStackTrace();
        }
    }

    /**
     * Centre un texte sur une ligne.
     *
     * @param contentStream Le flux de contenu PDFBox
     * @param text          Le texte à centrer
     * @param font          La police utilisée
     * @param fontSize      La taille de la police
     * @param pageWidth     La largeur de la page
     * @param yPosition     La position verticale du texte
     * @throws IOException En cas d'erreur d'écriture
     */
    private static void drawCenteredText(PDPageContentStream contentStream, String text, PDType1Font font, int fontSize, float pageWidth, float yPosition) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize; // Calculer la largeur du texte
        float startX = (pageWidth - textWidth) / 2; // Calculer la position x pour centrer
        contentStream.setFont(font, fontSize);
        contentStream.beginText();
        contentStream.newLineAtOffset(startX, yPosition); // Positionner le texte centré
        contentStream.showText(text);
        contentStream.endText();
    }

    /**
     * Découpe le texte pour qu'il respecte la largeur maximale d'une ligne.
     *
     * @param text      Le texte à découper
     * @param maxWidth  La largeur maximale
     * @param font      La police utilisée
     * @param fontSize  La taille de la police
     * @return Une liste de lignes découpées
     * @throws IOException En cas d'erreur
     */
    private static List<String> wrapText(String text, float maxWidth, PDType1Font font, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + word + " ";
            float textWidth = font.getStringWidth(testLine) / 1000 * fontSize;
            if (textWidth > maxWidth) {
                lines.add(currentLine.toString().trim());
                currentLine = new StringBuilder(word + " ");
            } else {
                currentLine.append(word).append(" ");
            }
        }

        if (!currentLine.toString().isEmpty()) {
            lines.add(currentLine.toString().trim());
        }

        return lines;
    }
}
