package arsenic.utils.interfaces;

import java.util.ArrayList;
import java.util.Collection;

public interface IContainer {

    Collection<IContainable> getContents();

    String getName();

    default Collection<IContainable> getContentsIncludingSubContainers() {
        Collection<IContainable> containables = new ArrayList<>();

        for(IContainable ic : getContents()) {
            containables.add(ic);

            if(ic instanceof IContainer) {
                containables.addAll(((IContainer) ic).getContentsIncludingSubContainers());
            }
        }

        return containables;
    }

}
