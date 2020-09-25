package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Core {

    List<String> talks = new ArrayList<>();

    public int add(int a, int b) {
        return a + b;
    }

    public void doNothing() {
    }

    public void doUnneccessaryStuff() {
        String s;
        int result = 41;
    }

    public List<String> addNewTalkForwad(List<String> list, String talk) {
        return addNewTalk(list, talk);
    }

    public List<String> addNewTalk(List<String> list, String talk) {
        List<String> mutableList = new ArrayList<>(list);
        if (talk != null) {
            mutableList.add(talk);
        }
        return Collections.unmodifiableList(mutableList);
    }

 //    public List<String> addBoeseNewTalk(List<String> list, String talk) {
 //       talks = list;
 //       return Collections.unmodifiableList(talks);
 //   }

}
