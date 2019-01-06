package io.github.hbusul;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Stack;

public class BasicXMLWriter {

    private BufferedWriter writer;
    private Stack<String> elementStack;
    private int indent = 0;
    private boolean first;
    private boolean valuePut;


    public BasicXMLWriter(BufferedWriter writer) {
        this.writer = writer;
        elementStack = new Stack<>();
        first = true;
        valuePut = false;
    }

    private void indent() throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write(" ");
        }
    }

    public void openTag(String tagName) throws IOException {
        valuePut = false;
        if (!first)
            writer.write("\n");
        indent();
        indent += 2;
        writer.write("<" + tagName + ">");
        elementStack.push(tagName);
        if (first)
            first = false;
    }

    public void closeTag(String tagName) throws IOException {
        indent -= 2;
        if(!valuePut){
            writer.write("\n");
            indent();
        }
        writer.write("</" + tagName + ">");
        String pop = elementStack.pop();
        if (!pop.equals(tagName)) {
            throw new RuntimeException("Incorrect close");
        }
        valuePut = false;
    }

    public void writeValue(String str) throws IOException {
        valuePut = true;
        writer.write(" " + escape(str) + " ");
    }

    private String escape(String str) {
        StringBuilder builder = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '"') {
                builder.append("&quot;");
            } else if (c == '&') {
                builder.append("&amp;");
            } else if (c == '\'') {
                builder.append("&apos;");
            } else if (c == '<') {
                builder.append("&lt;");
            } else if (c == '>') {
                builder.append("&gt;");
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }


}
