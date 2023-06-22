import org.example.Variant;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        String xmlFilePath = "path/to/your/xml/file.xml";
        String inputFile = "path/to/your/input/file.txt";
        String outputFile = "path/to/your/output/file.txt";

        try {
            List<Variant> variants = parseVariants(xmlFilePath);
            List<Variant> inputVariants = readVariantsFromFile(inputFile);

            if (inputVariants == null) {
                System.out.println("Invalid input variants");
                return;
            }

            variants.addAll(inputVariants);

            processVariants(variants, outputFile);
            System.out.println("Variants processed successfully!");
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
        }
    }

    private static List<Variant> parseVariants(String xmlFilePath) throws IOException, XMLStreamException {
        System.setProperty("entityExpansionLimit", "0");
        System.setProperty("totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");

        List<Variant> variants = new ArrayList<>();
        Set<String> variantIdentifiers = new HashSet<>();
        Map<String, String> omimIdMap = new HashMap<>();

        try (FileInputStream inputStream = new FileInputStream(xmlFilePath)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            boolean inSequenceLocation = false;

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        String elementName = reader.getLocalName();

                        if (elementName.equals("SequenceLocation")) {
                            inSequenceLocation = true;

                            String currentChromosome = reader.getAttributeValue(null, "Chr");
                            int currentStart = Integer.parseInt(reader.getAttributeValue(null, "start"));

                            String variantIdentifier = currentChromosome + "_" + currentStart;

                            if (!variantIdentifiers.contains(variantIdentifier)) {
                                variantIdentifiers.add(variantIdentifier);

                                Variant variant = new Variant();
                                variant.setChromosome(currentChromosome);
                                variant.setPosition(currentStart);

                                variants.add(variant);
                            }
                        } else if (elementName.equals("XRef")) {
                            String db = reader.getAttributeValue(null, "DB");
                            String type = reader.getAttributeValue(null, "Type");
                            String id = reader.getAttributeValue(null, "ID");

                            if (db.equals("OMIM") && type.equals("MIM")) {
                                omimIdMap.put(id, variantIdentifiers.iterator().next());
                            }
                        }

                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        if (reader.getLocalName().equals("SequenceLocation")) {
                            inSequenceLocation = false;
                        }

                        break;
                }
            }
        }

        processVariants(variants, omimIdMap);
        return variants;
    }

    private static void processVariants(List<Variant> variants, Map<String, String> omimIdMap) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (Variant variant : variants) {
            executor.submit(() -> {
                String variantIdentifier = variant.getChromosome() + "_" + variant.getPosition();
                String omimId = omimIdMap.getOrDefault(variantIdentifier, "null");
                variant.setOmimId(omimId);
            });
        }

        executor.shutdown();

        while (!executor.isTerminated()) {
            // Wait for all tasks to complete
        }
    }

    private static void writeOutput(List<Variant> variants, String outputFile) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(outputFile));
        writer.println("chr\tstart\talleleID\tRef.\tAlt.\tOMIM ID");

        for (Variant variant : variants) {
            writer.println(variant.getChromosome() + "\t" + variant.getPosition() + "\t" +
                    variant.getAlleleID() + "\t" + variant.getRef() + "\t" + variant.getAlt() +
                    "\t" + variant.getOmimId());
        }

        writer.close();
    }

//     private static List<Variant> readVariantsFromFile(String inputFile) throws IOException {
//         // Implement your logic to read variants from the input file
//         // and return a list of Variant objects
//         return null;
//     }
  
    private static List<Variant> readVariantsFromFile(String inputFile) {
          List<Variant> variants = new ArrayList<>();

          try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
              String line;
              while ((line = reader.readLine()) != null) {
                  String[] parts = line.trim().split("\\s+");
                  if (parts.length >= 5) {
                      String chromosome = parts[0];
                      int start = Integer.parseInt(parts[1]);
                      String alleleID = parts[2];
                      String ref = parts[3];
                      String alt = parts[4];

                      Variant variant = Variant.builder()
                              .chromosome(chromosome)
                              .position(start)
                              .alleleID(alleleID)
                              .ref(ref)
                              .alt(alt)
                              .build();

                      System.out.println("Read variant: " + variant.toString());

                      variants.add(variant);
                  } else {
                      System.out.println("Invalid variant data: " + line);
                  }
              }
          } catch (IOException | NumberFormatException e) {
              e.printStackTrace();
          }

          return variants;
      }
}
