package arsenic.module.property.impl;

import java.util.List;

import arsenic.module.property.Property;
import scala.actors.threadpool.Arrays;

public class FolderProperty extends Property<List<Property>>{

	//does not save the config of properties inside the folder
	//properties inside the folder cannot use @PropertyInfo

	private boolean open;

	protected FolderProperty(Property... values) {
		super(Arrays.asList(values));
	}

	public boolean isOpen() {
		return open;
	}
	
	public void toggleOpen() {
		open = !open;
	}

	public void setOpen(boolean open) {
		this.open = open;
	}

}
