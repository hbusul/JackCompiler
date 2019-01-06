package io.github.hbusul;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JackCompiler {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("help")) {
            usage();
            return;
        }

        String input = args[0];
        File file = new File(input);

        if (!file.exists()) {
            System.out.println("File or directory couldn't found");
            return;
        }

        ArrayList<String> inputs = new ArrayList<>();

        if (file.isDirectory()) {
            File[] listOfFiles = file.listFiles();
            if (listOfFiles != null)
                for (File f : listOfFiles) {
                    if (f.getName().endsWith(".jack"))
                        inputs.add(f.getAbsolutePath());
                }
        } else {
            inputs.add(file.getAbsolutePath());
        }

        if (args.length == 1) {
            for (String i : inputs) {
                System.out.printf("Compiling %s\n", i);
                try (CompilationEngine engine = new CompilationEngine(i)) {
                    engine.compileClass();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                dumpTokens(inputs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static void usage() {
        System.out.println("usage: JackCompiler input OPTIONS");
        System.out.println("input can be a file or a directory");
    }

    private static void dumpTokens(List<String> fileNames) throws IOException {
        for (String fileName : fileNames) {
            String outputFileName = fileName.substring(0, fileName.length() - 5) + "T.xml";
            BasicXMLWriter xmlWriter = new BasicXMLWriter(new BufferedWriter(new FileWriter(outputFileName)));
            xmlWriter.openTag("tokens");
            try (JackTokenizer tokenizer = new JackTokenizer(fileName)) {
                while (tokenizer.hasMoreTokens()) {
                    tokenizer.advance();
                    TokenType tokenType = tokenizer.getTokenType();
                    switch (tokenType) {
                        case KEYWORD:
                            xmlWriter.openTag("keyword");
                            xmlWriter.writeValue(tokenizer.keyword().toString().toLowerCase(Locale.US));
                            xmlWriter.closeTag("keyword");

                            break;
                        case SYMBOL:
                            xmlWriter.openTag("symbol");
                            xmlWriter.writeValue(tokenizer.symbol() + "");
                            xmlWriter.closeTag("symbol");
                            break;
                        case INT_CONST:
                            xmlWriter.openTag("integerConstant");
                            xmlWriter.writeValue(tokenizer.intVal() + "");
                            xmlWriter.closeTag("integerConstant");
                            break;
                        case STRING_CONST:
                            xmlWriter.openTag("stringConstant");
                            xmlWriter.writeValue(tokenizer.stringVal());
                            xmlWriter.closeTag("stringConstant");
                            break;
                        case IDENTIFIER:
                            xmlWriter.openTag("identifier");
                            xmlWriter.writeValue(tokenizer.identifier());
                            xmlWriter.closeTag("identifier");
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            xmlWriter.closeTag("tokens");
            xmlWriter.close();
        }
    }


}
