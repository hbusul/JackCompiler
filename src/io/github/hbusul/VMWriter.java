package io.github.hbusul;

import java.io.BufferedWriter;
import java.io.IOException;

class VMWriter implements AutoCloseable {
    private BufferedWriter writer;

    VMWriter(BufferedWriter writer) {
        this.writer = writer;
    }

    void writePush(String segment, int index) throws IOException {
        writer.write("push " + segment + " " + index + "\n");
    }

    void writePop(String segment, int index) throws IOException {
        writer.write("pop " + segment + " " + index + "\n");
    }

    void writeCall(String functionName, int argumentCount) throws IOException {
        writer.write("call " + functionName + " " + argumentCount + "\n");
    }

    void neg() throws IOException {
        writer.write("neg\n");
    }


    void ret() throws IOException {
        writer.write("return\n");
    }

    void writeLabel(String label) throws IOException {
        writer.write("label " + label + "\n");
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }


    void add() throws IOException {
        writer.write("add\n");
    }

    void sub() throws IOException {
        writer.write("sub\n");
    }

    void mult() throws IOException {
        writer.write("call Math.multiply 2\n");
    }

    void div() throws IOException {
        writer.write("call Math.divide 2\n");
    }

    void and() throws IOException {
        writer.write("and\n");
    }

    void or() throws IOException {
        writer.write("or\n");
    }

    void lt() throws IOException {
        writer.write("lt\n");
    }

    void gt() throws IOException {
        writer.write("gt\n");
    }

    void eq() throws IOException {
        writer.write("eq\n");
    }

    void not() throws IOException {
        writer.write("not\n");
    }

    void writeIfGoto(String label) throws IOException {
        writer.write("if-goto " + label + "\n");
    }

    void writeGoto(String label) throws IOException {
        writer.write("goto " + label + "\n");
    }

    void writeAlloc(int numberOfBlocks) throws IOException {
        writePush("constant", numberOfBlocks);
        writeCall("Memory.alloc", 1);
    }

    void writeFunction(String name, int numberOfLocalVar) throws IOException{
        writer.write("function " + name + " " + numberOfLocalVar + "\n");
    }
}
