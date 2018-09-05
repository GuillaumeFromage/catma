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
package de.catma.ui.repository;

import de.catma.document.source.SourceDocument;

public class MarkupCollectionItem {
	private String displayString;
	private boolean userMarkupCollectionItem = false;
	private String parentId;

	public MarkupCollectionItem(SourceDocument parent, String displayString) {
		this(parent, displayString, false);
	}
	
	public MarkupCollectionItem(
			SourceDocument parent,
			String displayString, boolean userMarkupCollectionItem) {
		this.parentId = parent.getID();
		this.displayString = displayString;
		this.userMarkupCollectionItem = userMarkupCollectionItem;
	}

	@Override
	public String toString() {
		return displayString;
	}
	
	public boolean isUserMarkupCollectionItem() {
		return userMarkupCollectionItem;
	}
	
	public String getParentId() {
		return parentId;
	}
}