package core;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Core {

    /**
     * Classify as SSEF
     */
    List<String> talks = new ArrayList<>();

    /**
     * Classify as SSEF
     */
    String string;

    /**
     * Classify as SSEF
     */
    public int add(int a, int b) {
        return a + b;
    }

    /**
     * Classify as not SEF
     */
    public void doNothing() {
    }

    /**
     * Classify as SSEF
     */
    public void doUnneccessaryStuff() {
        String s;
        int result = 41;
    }

    /**
     * Classify as SSEF
     */
    public List<String> addNewTalkForwad(List<String> list, String talk) {
        return addNewElement(list, talk);
    }

    /**
     * Classify as SSEF
     */
    public List<String> addNewElement(List<String> list, String talk) {
        List<String> mutableList = new ArrayList<>(list);
        if (talk != null) {
            mutableList.add(talk);
        }
        return Collections.unmodifiableList(mutableList);
    }

    /**
     * Classify as NotSEF
     */
    public List<String> addBoeseNewElement(List<String> list, String talk) {
        talks = list;
        return Collections.unmodifiableList(talks);
    }

    /**
     * Lazy initizialization, should be DSEF
     */
    public String getLazy() {
        if (string == null) {
            string = "Testtest";
        }
        return string;
    }

    /**
     * Random init, should be NotSEF
     */
    public String getRandomInit() {
        string = "Testtest" + LocalTime.now();

        return string;
    }

}
