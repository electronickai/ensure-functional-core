package playground;

import app.Application;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

public class PropertyTest {

    @Property
    boolean addNewElement_nullValuesAreNotAddedToTheList(@ForAll List<String> initialList, @ForAll("nullStrings") String nullString) {
        //Arrange
        int initialSize = initialList.size();
        //Act
        List<String> listWithNewElement = new Application().addNewElement(initialList, nullString);
        //Assert
        return listWithNewElement.size() == initialSize;
    }

    @Property
    boolean addNewElement_lengthIsOneBiggerThanBefore(@ForAll List<String> initialList, @ForAll String newElement) {
        //Arrange
        int initialSize = initialList.size();
        //Act
        List<String> listWithNewElement = new Application().addNewElement(initialList, newElement);
        //Assert
        return listWithNewElement.size() == initialSize + 1;
    }

    @Property
    boolean addNewElement_lastElementIsTheNewElement(@ForAll List<String> initialList, @ForAll String newElement) {
        //Act
        List<String> listWithNewElement = new Application().addNewElement(initialList, newElement);
        //Assert
        String lastElement = listWithNewElement.get(listWithNewElement.size() - 1);
        return lastElement.equals(newElement);
    }

    @Provide
    Arbitrary<String> nullStrings() {
        return Arbitraries.strings().injectNull(1);
    }

}
