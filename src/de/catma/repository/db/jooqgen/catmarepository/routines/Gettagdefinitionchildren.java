/**
 * This class is generated by jOOQ
 */
package de.catma.repository.db.jooqgen.catmarepository.routines;


import de.catma.repository.db.jooqgen.catmarepository.Catmarepository;

import javax.annotation.Generated;

import org.jooq.Parameter;
import org.jooq.impl.AbstractRoutine;


/**
 * This class is generated by jOOQ.
 */
@Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.7.2"
	},
	comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Gettagdefinitionchildren extends AbstractRoutine<java.lang.Void> {

	private static final long serialVersionUID = -1449043421;

	/**
	 * The parameter <code>catmarepository.getTagDefinitionChildren.startTagDefinitionID</code>.
	 */
	public static final Parameter<Integer> STARTTAGDEFINITIONID = createParameter("startTagDefinitionID", org.jooq.impl.SQLDataType.INTEGER, false);

	/**
	 * Create a new routine call instance
	 */
	public Gettagdefinitionchildren() {
		super("getTagDefinitionChildren", Catmarepository.CATMAREPOSITORY);

		addInParameter(STARTTAGDEFINITIONID);
	}

	/**
	 * Set the <code>startTagDefinitionID</code> parameter IN value to the routine
	 */
	public void setStarttagdefinitionid(Integer value) {
		setValue(STARTTAGDEFINITIONID, value);
	}
}
