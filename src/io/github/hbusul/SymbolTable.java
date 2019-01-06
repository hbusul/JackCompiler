package io.github.hbusul;

import java.util.HashMap;

class SymbolTable {
    private HashMap<String, Integer> hashMap;
    private HashMap<Integer, String> classNameHashMap;


    private int argument, field, local, stat;

    enum SymbolType {
        BOOLEAN, CHAR, CLASS_NAME, INT
    }

    enum SymbolKind {
        ARGUMENT, FIELD, LOCAL, STATIC
    }

    SymbolTable() {
        hashMap = new HashMap<>();
        classNameHashMap = new HashMap<>();
        argument = field = local = stat = 0;
    }

    void insertSymbol(String symbol, String className, SymbolKind kind){
        int val = insertSymbol(symbol, SymbolType.CLASS_NAME, kind);
        classNameHashMap.put(val, className);
    }

    int insertSymbol(String symbol, SymbolType type, SymbolKind kind) {
        int symbolType = 0;
        int symbolKind = 0;
        int symbolIndex = 0;

        switch (type) {
            case INT:
                symbolType = 1;
                break;
            case CHAR:
                symbolType = 2;
                break;
            case BOOLEAN:
                symbolType = 4;
                break;
            case CLASS_NAME:
                symbolType = 8;
                break;
        }

        switch (kind) {
            case FIELD:
                symbolKind = 16;
                symbolIndex = field;
                field++;
                break;
            case STATIC:
                symbolKind = 32;
                symbolIndex = stat;
                stat++;
                break;
            case LOCAL:
                symbolKind = 64;
                symbolIndex = local;
                local++;
                break;
            case ARGUMENT:
                symbolKind = 128;
                symbolIndex = argument;
                argument++;
                break;
        }
        int val = ((symbolKind | symbolType) << 24) | symbolIndex;
        hashMap.put(symbol, val);
        return val;
    }

    void print(){
        for(String symbol : hashMap.keySet()){
            int val = getVal(symbol);
            System.out.printf("Symbol: %s, Type: %s, Kind %s, Index %d\n", symbol, getType(val), getKind(val), getIndex(val));
        }



    }

    int getVal(String symbol) {
        Integer val = hashMap.get(symbol);
        if(val == null)
            return -1;
        return val;
    }

    int getNumberOfFields(){
        return field;
    }

    int getNumberOfLocalVariables(){
        return local;
    }

    String getClassName(int val){
        return classNameHashMap.get(val);
    }

    static int getIndex(int val) {
        return (val & ((1 << 24) - 1));
    }

    static SymbolKind getKind(int val) {
        if ((val & (1 << 31)) == (1 << 31)) return SymbolKind.ARGUMENT;
        if ((val & (1 << 30)) == (1 << 30)) return SymbolKind.LOCAL;
        if ((val & (1 << 29)) == (1 << 29)) return SymbolKind.STATIC;
        if ((val & (1 << 28)) == (1 << 28)) return SymbolKind.FIELD;
        return null;
    }

    static SymbolType getType(int val) {
        if ((val & (1 << 27)) == (1 << 27)) return SymbolType.CLASS_NAME;
        if ((val & (1 << 26)) == (1 << 26)) return SymbolType.BOOLEAN;
        if ((val & (1 << 25)) == (1 << 25)) return SymbolType.CHAR;
        if ((val & (1 << 24)) == (1 << 24)) return SymbolType.INT;
        return null;
    }
}
