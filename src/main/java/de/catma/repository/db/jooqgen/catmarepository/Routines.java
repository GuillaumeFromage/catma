/**
 * This class is generated by jOOQ
 */
package de.catma.repository.db.jooqgen.catmarepository;


import de.catma.repository.db.jooqgen.catmarepository.routines.Gettagdefinitionchildren;

import javax.annotation.Generated;

import org.jooq.Configuration;


/**
 * Convenience access to all stored procedures and functions in catmarepository
 */
@Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.7.2"
	},
	comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Routines {

	/**
	 * Call <code>catmarepository.getTagDefinitionChildren</code>
	 */
	public static void gettagdefinitionchildren(Configuration configuration, Integer starttagdefinitionid) {
		Gettagdefinitionchildren p = new Gettagdefinitionchildren();
		p.setStarttagdefinitionid(starttagdefinitionid);

		p.execute(configuration);
	}
}