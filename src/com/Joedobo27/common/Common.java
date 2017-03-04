package com.joedobo27.common;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("unused")
public class Common {

    private static ArrayList<Integer> exceptions;

    static {
        if (Objects.equals(exceptions,null))
            iniExceptions();
    }

    /**
     * Returning 73 serves as a way to disable certain code. This method is used as a expression editor hook to replace
     * the returned value of this.objectCreated.
     * if (template.isCombine() && this.objectCreated != 73) {
     *
     * @param check int value, which is an entry from ItemList.
     * @return return an int.
     */
    public static int checkSaneAmountsExceptionsHook(int check, int sourceMax, int targetMax){
        boolean isLargeSizeDifferential = exceptions.stream().anyMatch(value -> check == value);
        if (isLargeSizeDifferential) {
            return 73;
        }else{
            return check;
        }
    }

    public static ArrayList<Integer> getExceptions() {
        return exceptions;
    }

    public static void addExceptions(int[] ints){
        if (Objects.equals(exceptions, null))
            iniExceptions();
        Arrays.stream(ints)
                .distinct()
                .forEach(value -> {if (!exceptions.contains(value)){
                    exceptions.add(value);
                }});
    }

    private static void iniExceptions(){
        exceptions = new ArrayList<>();
        exceptions.add(73);
    }
}
