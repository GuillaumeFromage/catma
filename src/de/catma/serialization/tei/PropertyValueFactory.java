/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009  University Of Hamburg
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

package de.catma.serialization.tei;


/**
 * A factory that sets and gets values for a {@link Property}.
 *
 * @author Marco Petris
 *
 */
public interface PropertyValueFactory {

	/**
	 * Getter.
	 * @param teiElement the element that represents the {@link Property}
	 * @return the value of the {@link Property} represented by the given element.
	 */
	public Object getValue( TeiElement teiElement );

	/**
	 * Setter.
	 * @param teiElement the element that represents the {@link Property}
	 * @param value the value of the {@link Property} represented by the given element
	 */
	public void setValue( TeiElement teiElement, Object value );
	
}