package io.github.hbusul;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

class JackTokenizer implements java.lang.AutoCloseable {

    private BufferedReader reader;
    private String currentToken;
    private TokenType currentTokenType;
    private KeywordType currentKeywordType;
    private static final String SYMBOLS = "(){}[].,;+-[]*/&|<>=~";
    private String inputFileName;

    private String savedToken;
    private TokenType savedTokenType;
    private KeywordType savedKeywordType;


    JackTokenizer(String fileName) throws FileNotFoundException {
        reader = new BufferedReader(new FileReader(fileName));
        currentToken = null;
        currentTokenType = null;
        this.inputFileName = fileName;
    }

    String getInputFileName() {
        return inputFileName;
    }

    boolean hasMoreTokens() throws IOException {
        int c = ' ';
        while (c == ' ' || c == '\r' || c == '\t' || c == '\n' || c == '/') {
            if (c == '/') {
                reader.reset();
                reader.mark(2);
                reader.read();
                char b = (char) reader.read();
                if (b == '/') {
                    reader.readLine();
                }else if(b == '*'){
                    b = (char)reader.read();
                    char b2 = (char) reader.read();
                    while(b != '*' || b2 != '/'){
                        b = b2;
                        b2 = (char)reader.read();
                    }
                } else {
                    reader.reset();
                    return true;
                }
            }
            reader.mark(1);
            c = reader.read();
        }
        reader.reset();
        return c != -1;
    }

    void advance() throws IOException {
        StringBuilder tokenBuilder = new StringBuilder();
        int c = reader.read();
        //Integer Const
        if (c <= '9' && c >= '0') {
            while (c <= '9' && c >= '0') {
                tokenBuilder.append(c - '0');
                reader.mark(1);
                c = reader.read();
            }
            reader.reset();
            currentTokenType = TokenType.INT_CONST;
        } else if (c == '"') { //String const
            c = reader.read(); //we do not want the mark
            while (c != '"') {
                tokenBuilder.append((char) c);
                c = reader.read();
            }
            currentTokenType = TokenType.STRING_CONST;
        } else if (SYMBOLS.contains((char) c + "")) {
            tokenBuilder.append((char) c);
            currentTokenType = TokenType.SYMBOL;
        } else {
            while ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
                tokenBuilder.append((char) c);
                reader.mark(1);
                c = reader.read();
            }
            reader.reset();

            currentToken = tokenBuilder.toString();
            if (isKeyword(currentToken)) {
                this.currentTokenType = TokenType.KEYWORD;
            } else {
                this.currentTokenType = TokenType.IDENTIFIER;
            }
        }

        currentToken = tokenBuilder.toString();
    }

    KeywordType keyword() {
        return currentKeywordType;
    }

    char symbol() {
        return currentToken.charAt(0);
    }

    String identifier() {
        return currentToken;
    }

    int intVal() {
        return Integer.valueOf(currentToken);
    }

    String stringVal() {
        return currentToken;
    }

    private boolean isKeyword(String token) {
        for (KeywordType keywordType : KeywordType.values()) {
            String name = keywordType.name().toLowerCase(Locale.US);
            if (name.equals(token)) {
                currentKeywordType = keywordType;
                return true;
            }
        }
        return false;
    }

    void mark() throws IOException {
        savedKeywordType = currentKeywordType;
        savedToken = currentToken;
        savedTokenType = currentTokenType;
        reader.mark(100);
    }

    void reset() throws IOException {
        currentKeywordType = savedKeywordType;
        currentToken = savedToken;
        currentTokenType = savedTokenType;
        reader.reset();
    }

    TokenType getTokenType() {
        return currentTokenType;
    }

    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
