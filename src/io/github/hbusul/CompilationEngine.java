package io.github.hbusul;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;


@SuppressWarnings("Duplicates")
public class CompilationEngine implements AutoCloseable {

    private JackTokenizer tokenizer;
    private BasicXMLWriter xmlWriter;
    private SymbolTable classLevelSymbolTable;
    private SymbolTable subroutineLevelSymbolTable;
    private VMWriter outputWriter;
    private int nextLabelNumber;
    private String className;

    /*Each input file is a class */
    CompilationEngine(String inputFileName) throws IOException {
        tokenizer = new JackTokenizer(inputFileName);
        String fileName = inputFileName.substring(0, inputFileName.length() - 5);
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName + ".xml"));
        xmlWriter = new BasicXMLWriter(writer);
        outputWriter = new VMWriter(new BufferedWriter(new FileWriter(fileName + ".vm")));
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
        nextLabelNumber = 0;
    }

    private String generateLabel() {
        nextLabelNumber++;
        return "L" + (nextLabelNumber - 1);
    }

    private void consumeKeyword(KeywordType keywordType) throws IOException {
        if (tokenizer.getTokenType() == TokenType.KEYWORD) {
            if (tokenizer.keyword() != keywordType) {
                throw new RuntimeException("Expected a while keyword, found '" +
                        tokenizer.keyword() + "' keyword");
            }
        } else {
            throw new RuntimeException("Expected a keyword, found " + tokenizer.getTokenType());
        }
        xmlWriter.openTag("keyword");
        xmlWriter.writeValue(keywordType.toString().toLowerCase(Locale.US));
        xmlWriter.closeTag("keyword");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
    }

    private void consumeSymbol(char symbol) throws IOException {
        if (tokenizer.getTokenType() == TokenType.SYMBOL) {
            if (tokenizer.symbol() != symbol) {
                throw new RuntimeException(String.format("Expected '%c', found '%c'", symbol, tokenizer.symbol()));
            }
        } else {
            throw new RuntimeException("Expected a symbol, found " + tokenizer.getTokenType());
        }
        xmlWriter.openTag("symbol");
        xmlWriter.writeValue(symbol + "");
        xmlWriter.closeTag("symbol");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
    }

    private void consumeSymbolWeak(char symbol) throws IOException {
        if (tokenizer.getTokenType() != TokenType.SYMBOL) return; //don't throw exception
        if (tokenizer.symbol() != symbol) return; //don't throw exception
        xmlWriter.openTag("symbol");
        xmlWriter.writeValue(symbol + "");
        xmlWriter.closeTag("symbol");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
    }

    private int consumeIntConst() throws IOException {
        if (tokenizer.getTokenType() != TokenType.INT_CONST)
            throw new RuntimeException("Expected integer constant, found " + tokenizer.getTokenType());
        int value = tokenizer.intVal();
        xmlWriter.openTag("integerConstant");
        xmlWriter.writeValue(String.valueOf(value));
        xmlWriter.closeTag("integerConstant");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();

        return value;
    }

    private String consumeStringConst() throws IOException {
        if (tokenizer.getTokenType() != TokenType.STRING_CONST)
            throw new RuntimeException("Expected string constant, found " + tokenizer.getTokenType());

        String val = tokenizer.stringVal();
        xmlWriter.openTag("stringConstant");
        xmlWriter.writeValue(val);
        xmlWriter.closeTag("stringConstant");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();

        return val;
    }

    private KeywordType consumeKeywordConst() throws IOException {
        if (tokenizer.getTokenType() != TokenType.KEYWORD)
            throw new RuntimeException("Expected keyword, found " + tokenizer.getTokenType());
        KeywordType type = tokenizer.keyword();
        if (type != KeywordType.FALSE && type != KeywordType.TRUE &&
                type != KeywordType.NULL && type != KeywordType.THIS) {
            throw new RuntimeException("Unexpected keyword constant, " + tokenizer.keyword());
        }
        consumeKeyword(type);
        return type;
    }

    private boolean isTokenOperator() {
        String operators = "+-*/&|<>=";
        if (tokenizer.getTokenType() == TokenType.SYMBOL) {
            char symbol = tokenizer.symbol();
            return operators.contains(symbol + "");
        } else
            return false;
    }

    private boolean isTokenUnaryOperator() {
        String operators = "-~";
        if (tokenizer.getTokenType() == TokenType.SYMBOL) {
            char symbol = tokenizer.symbol();
            return operators.contains(symbol + "");
        } else
            return false;
    }

    private void compileWhileStatement() throws IOException {
        xmlWriter.openTag("whileStatement");
        consumeKeyword(KeywordType.WHILE);
        String label = generateLabel();
        outputWriter.writeLabel(label);
        String label2 = generateLabel();
        consumeSymbol('(');
        compileExpression();
        consumeSymbol(')');

        outputWriter.not();
        outputWriter.writeIfGoto(label2);

        consumeSymbol('{');
        compileStatements();
        consumeSymbol('}');

        outputWriter.writeGoto(label);
        outputWriter.writeLabel(label2);

        xmlWriter.closeTag("whileStatement");
    }

    private void compileIfStatement() throws IOException {
        xmlWriter.openTag("ifStatement");
        consumeKeyword(KeywordType.IF);
        consumeSymbol('(');
        compileExpression();
        consumeSymbol(')');

        outputWriter.not();
        //expression is compiled
        //generate label

        String L1 = generateLabel();
        outputWriter.writeIfGoto(L1);

        consumeSymbol('{');
        compileStatements();
        consumeSymbol('}');

        if (tokenizer.getTokenType() == TokenType.KEYWORD && tokenizer.keyword() == KeywordType.ELSE) {
            String L2 = generateLabel();
            outputWriter.writeGoto(L2);
            outputWriter.writeLabel(L1);
            consumeKeyword(KeywordType.ELSE);
            consumeSymbol('{');
            compileStatements();
            consumeSymbol('}');
            outputWriter.writeLabel(L2);
        } else {
            outputWriter.writeLabel(L1);
        }

        xmlWriter.closeTag("ifStatement");
    }

    private void compileLetStatement() throws IOException {
        xmlWriter.openTag("letStatement");
        consumeKeyword(KeywordType.LET);
        String varName = consumeIdentifier(); //identifier is a variable name

        int val = subroutineLevelSymbolTable.getVal(varName);
        if (val == -1) val = classLevelSymbolTable.getVal(varName);
        if (val == -1) throw new RuntimeException("Symbol " + varName + " could not be found in symbol table");

        SymbolTable.SymbolKind kind = SymbolTable.getKind(val);
        if (kind == null) throw new RuntimeException("Undefined Symbol Kind!");

        if (tokenizer.symbol() == '[') {
            //before calculating the expression push the base address
            switch (kind) {
                case ARGUMENT:
                    outputWriter.writePush("argument", SymbolTable.getIndex(val));
                    break;
                case FIELD:
                    outputWriter.writePush("this", SymbolTable.getIndex(val));
                    break;
                case LOCAL:
                    outputWriter.writePush("local", SymbolTable.getIndex(val));
                    break;
                case STATIC:
                    outputWriter.writePush("static", SymbolTable.getIndex(val));
                    break;
            }

            consumeSymbol('[');
            compileExpression();
            consumeSymbol(']');

            //add the base address + offset which will be the address of the array
            outputWriter.add();
            // store the value in the temp
            outputWriter.writePop("temp", 0);

            consumeSymbol('=');
            compileExpression();

            consumeSymbol(';');

            //after the calculation
            outputWriter.writePush("temp", 0);
            outputWriter.writePop("pointer", 1);
            outputWriter.writePop("that", 0);
            //write the result back to array
        } else {
            //not an array manipulation
            consumeSymbol('=');
            compileExpression();
            consumeSymbol(';');

            switch (kind) {
                case ARGUMENT:
                    outputWriter.writePop("argument", SymbolTable.getIndex(val));
                    break;
                case FIELD:
                    outputWriter.writePop("this", SymbolTable.getIndex(val));
                    break;
                case LOCAL:
                    outputWriter.writePop("local", SymbolTable.getIndex(val));
                    break;
                case STATIC:
                    outputWriter.writePop("static", SymbolTable.getIndex(val));
                    break;
            }
        }


        xmlWriter.closeTag("letStatement");
    }

    private void compileDoStatement() throws IOException {
        xmlWriter.openTag("doStatement");
        consumeKeyword(KeywordType.DO);
        compileSubRoutineCall();
        outputWriter.writePop("temp", 0);
        consumeSymbol(';');
        xmlWriter.closeTag("doStatement");
    }

    private void compileReturnStatement() throws IOException {
        xmlWriter.openTag("returnStatement");
        consumeKeyword(KeywordType.RETURN);
        if (tokenizer.getTokenType() != TokenType.SYMBOL) {
            //we have something to return
            compileExpression();
            outputWriter.ret();
        } else {
            //we will return a dummy variable
            outputWriter.writePush("constant", 0);
            outputWriter.ret();
        }
        consumeSymbol(';');
        xmlWriter.closeTag("returnStatement");

    }

    private void compileStatements() throws IOException {
        xmlWriter.openTag("statements");
        while (tokenizer.getTokenType() == TokenType.KEYWORD) {
            boolean exit = false;
            switch (tokenizer.keyword()) {
                case WHILE:
                    compileWhileStatement();
                    break;
                case IF:
                    compileIfStatement();
                    break;
                case LET:
                    compileLetStatement();
                    break;
                case DO:
                    compileDoStatement();
                    break;
                case RETURN:
                    compileReturnStatement();
                    break;
                default:
                    exit = true;
                    break;
            }
            if (exit) break;
        }
        xmlWriter.closeTag("statements");
    }

    void compileClass() throws IOException {
        classLevelSymbolTable = new SymbolTable();

        xmlWriter.openTag("class");
        consumeKeyword(KeywordType.CLASS);
        className = consumeIdentifier();
        consumeSymbol('{');

        while (tokenizer.getTokenType() == TokenType.KEYWORD &&
                (tokenizer.keyword() == KeywordType.STATIC || tokenizer.keyword() == KeywordType.FIELD)) {
            compileClassVarDec();
        }

    /*    System.out.println("Class level symbol table");
        classLevelSymbolTable.print();
        System.out.println();
*/

        while (tokenizer.getTokenType() == TokenType.KEYWORD && (tokenizer.keyword() == KeywordType.CONSTRUCTOR ||
                tokenizer.keyword() == KeywordType.FUNCTION || tokenizer.keyword() == KeywordType.METHOD)) {
            compileSubroutineDec();
        }

        consumeSymbol('}');
        xmlWriter.closeTag("class");

    }

    private void compileClassVarDec() throws IOException {
        xmlWriter.openTag("classVarDec");
        if (tokenizer.getTokenType() != TokenType.KEYWORD)
            throw new RuntimeException("Expected keyword, found " + tokenizer.getTokenType());


        /*Symbol info*/

        SymbolTable.SymbolKind symbolKind;
        SymbolTable.SymbolType symbolType = null;
        //static or field
        KeywordType keywordType = tokenizer.keyword();
        if (keywordType == KeywordType.STATIC || keywordType == KeywordType.FIELD) {
            symbolKind = keywordType == KeywordType.STATIC ? SymbolTable.SymbolKind.STATIC : SymbolTable.SymbolKind.FIELD;
            consumeKeyword(keywordType);
        } else {
            throw new RuntimeException("Expected static or field, found " + keywordType);
        }

        String className = null;
        //int, boolean, char or className
        if (tokenizer.getTokenType() == TokenType.IDENTIFIER) {
            className = consumeIdentifier();
            symbolType = SymbolTable.SymbolType.CLASS_NAME;
        } else if (tokenizer.getTokenType() == TokenType.KEYWORD) {
            keywordType = tokenizer.keyword();
            if (keywordType == KeywordType.CHAR) {
                consumeKeyword(keywordType);
                symbolType = SymbolTable.SymbolType.CHAR;
            } else if (keywordType == KeywordType.BOOLEAN) {
                consumeKeyword(keywordType);
                symbolType = SymbolTable.SymbolType.BOOLEAN;
            } else if (keywordType == KeywordType.INT) {
                consumeKeyword(keywordType);
                symbolType = SymbolTable.SymbolType.INT;
            } else {
                throw new RuntimeException("Unexpected type " + keywordType);
            }
        }

        //varName
        ArrayList<String> varNames = new ArrayList<>();
        varNames.add(consumeIdentifier());

        while (tokenizer.getTokenType() == TokenType.SYMBOL && tokenizer.symbol() == ',') {
            consumeSymbol(',');
            varNames.add(consumeIdentifier());
        }
        consumeSymbol(';');


        for (String symbol : varNames) {
            if (symbolType == SymbolTable.SymbolType.CLASS_NAME)
                classLevelSymbolTable.insertSymbol(symbol, className, symbolKind);
            else
                classLevelSymbolTable.insertSymbol(symbol, symbolType, symbolKind);
        }
        xmlWriter.closeTag("classVarDec");
    }

    private void compileSubroutineDec() throws IOException {
        subroutineLevelSymbolTable = new SymbolTable(); //reset the table

        boolean isConstructor = false;
        boolean isMethod = false;
        xmlWriter.openTag("subroutineDec");
        if (tokenizer.getTokenType() != TokenType.KEYWORD)
            throw new RuntimeException("Expected KEYWORD, found " + tokenizer.getTokenType());

        KeywordType keywordType = tokenizer.keyword();

        if (keywordType == KeywordType.METHOD) {
            subroutineLevelSymbolTable.insertSymbol("this", SymbolTable.SymbolType.CLASS_NAME,
                    SymbolTable.SymbolKind.ARGUMENT);
            isMethod = true;
        } else if (keywordType == KeywordType.CONSTRUCTOR) {
            isConstructor = true;
        } else if (keywordType != KeywordType.FUNCTION) {
            throw new RuntimeException("Unexpected keyword " + keywordType);
        }

        consumeKeyword(keywordType);


        TokenType tokenType = tokenizer.getTokenType();
        if (tokenType == TokenType.IDENTIFIER) {
            consumeIdentifier();
        } else if (tokenType == TokenType.KEYWORD) {
            keywordType = tokenizer.keyword();
            if (keywordType == KeywordType.VOID || keywordType == KeywordType.INT || keywordType == KeywordType.CHAR ||
                    keywordType == KeywordType.BOOLEAN) {
                consumeKeyword(keywordType);
            } else {
                throw new RuntimeException("Unexpected keyword, " + keywordType);
            }
        } else {
            throw new RuntimeException("Expected IDENTIFIER or KEYWORD, found " + tokenType);
        }

        String currentFunctionName = consumeIdentifier(); // function name
        consumeSymbol('(');
        compileParameterList(); //get the arguments
        consumeSymbol(')');
        compileSubroutineBody(isConstructor, isMethod, currentFunctionName);

        xmlWriter.closeTag("subroutineDec");
    }

    private void consumeType() throws IOException {
        //type can be int char boolean or identifier
        TokenType tokenType = tokenizer.getTokenType();
        if (tokenType == TokenType.IDENTIFIER) {
            consumeIdentifier();
        } else if (tokenType == TokenType.KEYWORD) {
            KeywordType keywordType = tokenizer.keyword();
            if (keywordType == KeywordType.INT || keywordType == KeywordType.CHAR || keywordType == KeywordType.BOOLEAN) {
                consumeKeyword(keywordType);
            } else {
                throw new RuntimeException("Unexpected keyword, " + keywordType);
            }
        } else {
            throw new RuntimeException("Expected type, found " + tokenType);
        }
    }

    private void compileParameterList() throws IOException {
        xmlWriter.openTag("parameterList");
        TokenType tokenType = tokenizer.getTokenType();
        if (tokenType == TokenType.SYMBOL && tokenizer.symbol() == ')') { //no parameter
            xmlWriter.closeTag("parameterList");
            return;
        }

        do {
            consumeSymbolWeak(',');
            SymbolTable.SymbolType symbolType;
            String className = null;
            if (tokenizer.getTokenType() == TokenType.IDENTIFIER) {
                symbolType = SymbolTable.SymbolType.CLASS_NAME;
                className = consumeIdentifier();
            } else if (tokenizer.keyword() == KeywordType.INT) symbolType = SymbolTable.SymbolType.INT;
            else if (tokenizer.keyword() == KeywordType.CHAR) symbolType = SymbolTable.SymbolType.CHAR;
            else if (tokenizer.keyword() == KeywordType.BOOLEAN) symbolType = SymbolTable.SymbolType.BOOLEAN;
            else throw new RuntimeException("Unexpected keyword: " + tokenizer.keyword());

            if (className == null) //If we hadn't consumed it yet
                consumeType();
            String name = consumeIdentifier();

            if (symbolType == SymbolTable.SymbolType.CLASS_NAME)
                subroutineLevelSymbolTable.insertSymbol(name, className, SymbolTable.SymbolKind.ARGUMENT);
            else
                subroutineLevelSymbolTable.insertSymbol(name, symbolType, SymbolTable.SymbolKind.ARGUMENT);

        } while (tokenizer.getTokenType() == TokenType.SYMBOL && tokenizer.symbol() == ',');
        xmlWriter.closeTag("parameterList");
    }

    private void compileSubroutineBody(boolean isConstructor, boolean isMethod, String currentFunctionName) throws IOException {
        xmlWriter.openTag("subroutineBody");
        consumeSymbol('{');
        while (tokenizer.getTokenType() == TokenType.KEYWORD && tokenizer.keyword() == KeywordType.VAR)
            compileVarDec();

        outputWriter.writeFunction(className + "." + currentFunctionName, subroutineLevelSymbolTable.getNumberOfLocalVariables());

        if (isConstructor) {
            //need to know number of fields
            outputWriter.writeAlloc(classLevelSymbolTable.getNumberOfFields());
            outputWriter.writePop("pointer", 0); //pop the value to this
        } else if (isMethod) {
            //if it is a method, first argument is the object itself
            outputWriter.writePush("argument", 0);
            outputWriter.writePop("pointer", 0);
        }

        compileStatements();
        consumeSymbol('}');
        xmlWriter.closeTag("subroutineBody");
    }

    private void compileVarDec() throws IOException {
        xmlWriter.openTag("varDec");
        if (tokenizer.getTokenType() != TokenType.KEYWORD || tokenizer.keyword() != KeywordType.VAR)
            throw new RuntimeException("Expected variable declaration!");

        consumeKeyword(KeywordType.VAR);
        SymbolTable.SymbolType symbolType = SymbolTable.SymbolType.INT;
        String className = null;
        if (tokenizer.getTokenType() == TokenType.IDENTIFIER) {
            symbolType = SymbolTable.SymbolType.CLASS_NAME;
            className = consumeIdentifier();
        } else if (tokenizer.getTokenType() == TokenType.KEYWORD) {
            if (tokenizer.keyword() == KeywordType.CHAR) symbolType = SymbolTable.SymbolType.CHAR;
            else if (tokenizer.keyword() == KeywordType.BOOLEAN) symbolType = SymbolTable.SymbolType.BOOLEAN;
            else if (tokenizer.keyword() != KeywordType.INT)
                throw new RuntimeException("Unexpected keyword: " + tokenizer.keyword());
        }

        if (className == null) //if we hadnt consumed yet
            consumeType();

        ArrayList<String> names = new ArrayList<>();
        names.add(consumeIdentifier());
        while (tokenizer.getTokenType() == TokenType.SYMBOL && tokenizer.symbol() == ',') {
            consumeSymbol(',');
            names.add(consumeIdentifier());
        }
        consumeSymbol(';');

        for (String symbol : names) {
            if (symbolType != SymbolTable.SymbolType.CLASS_NAME)
                subroutineLevelSymbolTable.insertSymbol(symbol, symbolType, SymbolTable.SymbolKind.LOCAL);
            else subroutineLevelSymbolTable.insertSymbol(symbol, className, SymbolTable.SymbolKind.LOCAL);
        }

        xmlWriter.closeTag("varDec");
    }

    private String consumeIdentifier() throws IOException {
        if (tokenizer.getTokenType() != TokenType.IDENTIFIER)
            throw new RuntimeException("Expected token identifier, found " + tokenizer.getTokenType());
        String identifier = tokenizer.identifier();
        xmlWriter.openTag("identifier");
        xmlWriter.writeValue(identifier);
        xmlWriter.closeTag("identifier");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
        return identifier;
    }

    private void compileExpression() throws IOException {
        xmlWriter.openTag("expression");
        compileTerm();
        while (isTokenOperator()) {
            char op = consumeOp();
            compileTerm();
            if (op == '+') outputWriter.add();
            else if (op == '-') outputWriter.sub();
            else if (op == '*') outputWriter.mult();
            else if (op == '/') outputWriter.div();
            else if (op == '&') outputWriter.and();
            else if (op == '|') outputWriter.or();
            else if (op == '<') outputWriter.lt();
            else if (op == '>') outputWriter.gt();
            else if (op == '=') outputWriter.eq();
        }
        xmlWriter.closeTag("expression");
    }

    private char consumeOp() throws IOException {
        if (!isTokenOperator())
            throw new RuntimeException("Current token is not an operator");
        xmlWriter.openTag("symbol");
        char symbol = tokenizer.symbol();
        xmlWriter.writeValue(symbol + "");
        xmlWriter.closeTag("symbol");
        if (tokenizer.hasMoreTokens())
            tokenizer.advance();
        return symbol;
    }

    private char consumeUnaryOp() throws IOException {
        if (!isTokenUnaryOperator())
            throw new RuntimeException("Current token is not an unary operator");
        xmlWriter.openTag("symbol");
        char symbol = tokenizer.symbol();
        xmlWriter.writeValue(symbol + "");
        xmlWriter.closeTag("symbol");

        if (tokenizer.hasMoreTokens())
            tokenizer.advance();

        return symbol;
    }

    private void compileTerm() throws IOException {
        xmlWriter.openTag("term");

        TokenType tokenType = tokenizer.getTokenType();
        if (tokenType == TokenType.KEYWORD) {
            KeywordType type = consumeKeywordConst();
            if (type == KeywordType.FALSE) outputWriter.writePush("constant", 0);
            else if (type == KeywordType.TRUE) {
                outputWriter.writePush("constant", 1);
                outputWriter.neg();
            } else if (type == KeywordType.NULL) outputWriter.writePush("constant", 0);
            else if (type == KeywordType.THIS) outputWriter.writePush("pointer", 0);
        } else if (tokenType == TokenType.INT_CONST) {
            int constant = consumeIntConst();
            outputWriter.writePush("constant", constant);
        } else if (tokenType == TokenType.STRING_CONST) {
            String strConst = consumeStringConst();
            int len = strConst.length();
            outputWriter.writePush("constant", len);
            outputWriter.writeCall("String.new", 1);
            for (int i = 0; i < len; i++) {
                char c = strConst.charAt(i);
                outputWriter.writePush("constant", (int) c);
                outputWriter.writeCall("String.appendChar", 2);
            }

        } else if (tokenType == TokenType.SYMBOL) {
            char symbol = tokenizer.symbol();
            if (symbol == '(') {
                consumeSymbol('(');
                compileExpression();
                consumeSymbol(')');
            } else if (isTokenUnaryOperator()) {
                char op = consumeUnaryOp();
                compileTerm();
                if (op == '-') outputWriter.neg();
                else if (op == '~') outputWriter.not();
            } else {
                throw new RuntimeException("Unexpected symbol, " + symbol);
            }
        } else {
            if (tokenType == TokenType.IDENTIFIER) {
                tokenizer.mark(); //save the current values
                if (tokenizer.hasMoreTokens())
                    tokenizer.advance();
                tokenType = tokenizer.getTokenType();

                if (tokenType == TokenType.SYMBOL) {
                    char symbol = tokenizer.symbol();
                    if (symbol == '[') { //array access
                        tokenizer.reset();
                        String arrayName = consumeIdentifier();
                        int val;
                        if ((val = subroutineLevelSymbolTable.getVal(arrayName)) == -1)
                            val = classLevelSymbolTable.getVal(arrayName);
                        if (val == -1) throw new RuntimeException("WTF");
                        SymbolTable.SymbolKind kind = SymbolTable.getKind(val);
                        if (kind == null) throw new RuntimeException("WTF");
                        outputWriter.writePush(kind.toString().toLowerCase(Locale.US).replace(
                                "field","local"), SymbolTable.getIndex(val));

                        consumeSymbol('[');
                        compileExpression();
                        consumeSymbol(']');

                        outputWriter.add();
                        outputWriter.writePop("pointer", 1);
                        outputWriter.writePush("that", 0);
                    } else if (symbol == '.' || symbol == '(') { //method or function call
                        tokenizer.reset();
                        compileSubRoutineCall();
                    } else { //only variable name
                        tokenizer.reset();
                        String variableName = consumeIdentifier();
                        //first look subroutine-level symbol table

                        int val = subroutineLevelSymbolTable.getVal(variableName);
                        if (val == -1) {
                            val = classLevelSymbolTable.getVal(variableName);
                            if (val == -1) throw new RuntimeException("Symbol could not be found in the table");
                        }

                        SymbolTable.SymbolKind kind = SymbolTable.getKind(val);
                        if (kind == null) {
                            throw new RuntimeException("Unknown variable kind");
                        }

                        switch (kind) {
                            case ARGUMENT:
                                outputWriter.writePush("argument", SymbolTable.getIndex(val));
                                break;
                            case FIELD:
                                outputWriter.writePush("this", SymbolTable.getIndex(val));
                                break;
                            case LOCAL:
                                outputWriter.writePush("local", SymbolTable.getIndex(val));
                                break;
                            case STATIC:
                                outputWriter.writePush("static", SymbolTable.getIndex(val));
                                break;
                        }
                    }
                }

            } else {
                throw new RuntimeException("Unexpected token, " + tokenType);
            }
        }
        xmlWriter.closeTag("term");

    }

    private int compileExpressionList() throws IOException {
        int numOfArgumentsProvided = 0;
        xmlWriter.openTag("expressionList");
        if (tokenizer.getTokenType() == TokenType.SYMBOL && tokenizer.symbol() == ')') {
            xmlWriter.closeTag("expressionList");
            //it is an empty expresison list
            return 0;
        } else {
            compileExpression();
            numOfArgumentsProvided++;
            while (tokenizer.getTokenType() == TokenType.SYMBOL && tokenizer.symbol() == ',') {
                consumeSymbol(',');
                compileExpression();
                numOfArgumentsProvided++;
            }
        }
        xmlWriter.closeTag("expressionList");
        return numOfArgumentsProvided;
    }

    private void compileSubRoutineCall() throws IOException {
        StringBuilder functionName = new StringBuilder();
        String identifier = consumeIdentifier();
        char symbol = tokenizer.symbol();
        boolean isMethodCall = false;
        if (symbol == '.') {
            if (subroutineLevelSymbolTable.getVal(identifier) != -1 || classLevelSymbolTable.getVal(identifier) != -1) {
                isMethodCall = true;
                String className;
                int i;
                if ((i = subroutineLevelSymbolTable.getVal(identifier)) != -1) {
                    className = subroutineLevelSymbolTable.getClassName(i);
                } else {
                    i = classLevelSymbolTable.getVal(identifier);
                    className = classLevelSymbolTable.getClassName(i);
                }

                SymbolTable.SymbolKind kind = SymbolTable.getKind(i);
                switch (kind) {
                    case ARGUMENT:
                        outputWriter.writePush("argument", SymbolTable.getIndex(i));
                        break;
                    case FIELD:
                        outputWriter.writePush("this", SymbolTable.getIndex(i));
                        break;
                    case LOCAL:
                        outputWriter.writePush("local", SymbolTable.getIndex(i));
                        break;
                    case STATIC:
                        outputWriter.writePush("static", SymbolTable.getIndex(i));
                        break;
                }

                functionName.append(className);
            } else {
                functionName.append(identifier);
            }
            consumeSymbol('.');
            functionName.append(".");
            functionName.append(consumeIdentifier());
        } else {
            outputWriter.writePush("pointer", 0);
            functionName.append(className);
            functionName.append('.');
            isMethodCall = true;
            functionName.append(identifier);
        }

        consumeSymbol('(');
        int numOfArgProvided = compileExpressionList();
        consumeSymbol(')');

        outputWriter.writeCall(functionName.toString(), numOfArgProvided + (isMethodCall ? 1 : 0));
    }

    @Override
    public void close() {
        try {
            xmlWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            outputWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
