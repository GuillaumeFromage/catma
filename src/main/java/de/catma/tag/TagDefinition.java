/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009-2013  University Of Hamburg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.catma.tag;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import de.catma.util.IDGenerator;

/**
 * A definition of a tag. That is a type of a {@link TagInstance}.
 * 
 * @author marco.petris@web.de
 */
public class TagDefinition {

	private final String uuid;
	private String name;
	private final Map<String,PropertyDefinition> systemPropertyDefinitions;
	private final Map<String,PropertyDefinition> userDefinedPropertyDefinitions;
	private final String parentUuid;
	private String tagsetDefinitionUuid;
	private transient boolean contribution = false;

	/**
	 * @param uuid a CATMA uuid see {@link de.catma.util.IDGenerator}
	 * @param name the name of the definition
	 * @param version the version of the definition
	 * @param parentUuid the CATMA uuid of the parent or <code>null</code> if this
	 * is already a top level definition
	 */
	public TagDefinition(
			String uuid, 
			String name,  
			String parentUuid, String tagsetDefinitionUuid) {

		systemPropertyDefinitions = new HashMap<String, PropertyDefinition>();
		userDefinedPropertyDefinitions = new HashMap<String, PropertyDefinition>();
		addSystemPropertyDefinition(
			new PropertyDefinition(
				new IDGenerator().generate(PropertyDefinition.SystemPropertyName.catma_markuptimestamp.name()), 
				PropertyDefinition.SystemPropertyName.catma_markuptimestamp.name(),
				Collections.emptyList()));
		this.uuid = uuid;
		this.name = name;
		if (parentUuid == null) {
			this.parentUuid = "";
		} else {
			this.parentUuid = parentUuid;			
		}
		this.tagsetDefinitionUuid = tagsetDefinitionUuid;
	}

	/**
	 * Copy constructor.
	 * @param toCopy
	 */
	public TagDefinition(TagDefinition toCopy) {
		this(toCopy, toCopy.getUuid(), toCopy.getParentUuid(), toCopy.getTagsetDefinitionUuid());
	}
	
	
	public TagDefinition(TagDefinition toCopy, String uuid, String parentUuid, String tagsetDefinitionUuid) {
		this(uuid, 
				toCopy.name, 
				parentUuid, tagsetDefinitionUuid);
		
		for (PropertyDefinition pd : toCopy.getSystemPropertyDefinitions()) {
			addSystemPropertyDefinition(new PropertyDefinition(pd));
		}
		for (PropertyDefinition pd : toCopy.getUserDefinedPropertyDefinitions()) {
			addUserDefinedPropertyDefinition(new PropertyDefinition(pd));
		}	
	}

	@Override
	public String toString() {
		return "TAG_DEF[" + name 
				+ ",u#" + uuid +","
				+((parentUuid.isEmpty()) ? "]" : (",#"+parentUuid+"]"));
	}

	/**
	 * See {@link PropertyDefinition.SystemPropertyName} for possibilities.
	 * @param propertyDefinition
	 */
	public void addSystemPropertyDefinition(PropertyDefinition propertyDefinition) {
		systemPropertyDefinitions.put(propertyDefinition.getName(), propertyDefinition);
	}
	
	public void addUserDefinedPropertyDefinition(PropertyDefinition propertyDefinition) {
		userDefinedPropertyDefinitions.put(propertyDefinition.getUuid(), propertyDefinition);
	}	
	
	/**
	 * @return the CATMA uuid see {@link de.catma.util.IDGenerator}
	 */
	public String getUuid() {
		return uuid;
	}

	/**
	 * @param name {@link #getName() name} of the PropertyDefinition
	 * @return the corresponding PropertyDefinition or <code>null</code> 
	 */
	public PropertyDefinition getPropertyDefinition(String name) {
		if (systemPropertyDefinitions.containsKey(name)) {
			return systemPropertyDefinitions.get(name);
		}
		else {
			return userDefinedPropertyDefinitions
					.values()
					.stream()
					.filter(pd -> pd.getName().equals(name))
					.findFirst()
					.orElse(null);
		}
	}
	
	/**
	 * @return non modifiable collection of user defined properties
	 */
	public Collection<PropertyDefinition> getUserDefinedPropertyDefinitions() {
		return Collections.unmodifiableCollection(userDefinedPropertyDefinitions.values());
	}
	
	/**
	 * @return the UUID of the parent TagDefinition or an empty String if this is
	 * 			a toplevel TagDefinittion. This method never returns <code>null</code>.
	 */
	public String getParentUuid() {
		return parentUuid;
	}

	public String getTagsetDefinitionUuid() {
		return this.tagsetDefinitionUuid;
	}

	public void setTagsetDefinitionUuid(String tagsetDefinitionUuid) {
		this.tagsetDefinitionUuid = tagsetDefinitionUuid;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * @return see {@link PropertyDefinition.SystemPropertyName#catma_displaycolor}
	 */
	public String getColor() {
		return getPropertyDefinition(
			PropertyDefinition.SystemPropertyName.catma_displaycolor.name()).getFirstValue();
	}

	public String getHexColor() {
		return getHexColor(getColor());
	}

	public static String getHexColor(String propertyValue) {
		int rgb = Integer.parseInt(propertyValue);
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		return String.format("#%02x%02x%02x", red, green, blue);

	}
	
	public String getAuthor() {
		PropertyDefinition authorPropertyDef =  getPropertyDefinition(
			PropertyDefinition.SystemPropertyName.catma_markupauthor.name());
		if (authorPropertyDef != null) {
			return authorPropertyDef.getFirstValue();
		}
		else {
			return null;
		}
	}

	public Collection<PropertyDefinition> getSystemPropertyDefinitions() {
		return Collections.unmodifiableCollection(
				systemPropertyDefinitions.values());
	}
	
	public void setColor(String colorAsRgbInt) {
		getPropertyDefinition(
			PropertyDefinition.SystemPropertyName.catma_displaycolor.name()).
				setValue(colorAsRgbInt);
	}
	
	public void setAuthor(String author) {
		getPropertyDefinition(
			PropertyDefinition.SystemPropertyName.catma_markupauthor.name()).
				setValue(author);
	}

	public void removeUserDefinedPropertyDefinition(PropertyDefinition propertyDefinition) {
		this.userDefinedPropertyDefinitions.remove(propertyDefinition.getUuid());
	}

	public PropertyDefinition getPropertyDefinitionByUuid(String uuid) {
		return this.userDefinedPropertyDefinitions.get(uuid);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TagDefinition other = (TagDefinition) obj;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		return true;
	}
	
	Stream<TagDefinition> directChildrenStream(TagsetDefinition tagset) {
		return Stream.concat(Stream.of(this), tagset.getDirectChildren(this).stream().flatMap(child -> child.directChildrenStream(tagset)));
	}

	public boolean mergeAdditive(TagDefinition tag) {
		
		for (PropertyDefinition propertyDef : tag.getUserDefinedPropertyDefinitions()) {
			if (!this.userDefinedPropertyDefinitions.containsKey(propertyDef.getUuid())) {
				propertyDef.setContribution(true);
				addUserDefinedPropertyDefinition(propertyDef);
				contribution = true;
			}
			
			// we do not alter the list of possible values for already exising PropertyDefinitions
			// because when working with latest contributions we expect to be in readonly mode anyway 
			// and possible values are only relevant for adding new annotations
		}
		
		return contribution;
	}
	
	public boolean isContribution() {
		return contribution;
	}
	
	public void setContribution(boolean contribution) {
		this.contribution = contribution;
	}
}
