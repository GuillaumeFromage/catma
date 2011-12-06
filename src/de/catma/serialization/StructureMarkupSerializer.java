package de.catma.serialization;

import java.io.InputStream;

import de.catma.core.document.standoffmarkup.structure.StructureMarkupCollection;

public interface StructureMarkupSerializer {
	
	public void serialize(StructureMarkupCollection structureMarkupCollection);
	public StructureMarkupCollection deserialize(InputStream inputStream);

}