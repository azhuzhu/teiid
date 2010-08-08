/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.sql.proc;

import org.teiid.core.util.EquivalenceUtil;
import org.teiid.core.util.HashCodeUtil;
import org.teiid.query.sql.LanguageVisitor;
import org.teiid.query.sql.lang.Command;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.Expression;
import org.teiid.query.sql.symbol.ScalarSubquery;


/**
 * <p> This class represents an assignment statement in the storedprocedure language.
 * It extends the <code>Statement</code> that could part of a <code>Block</code>.  This
 * statement holds references to the variable and it's value which could be an 
 * <code>Expression</code> or a <code>Command</code>.</p>
 */
public class AssignmentStatement extends Statement implements ExpressionStatement {

	// the variable to which a value is assigned
	private ElementSymbol variable;
	    
    private Expression value;

	/**
	 * Constructor for AssignmentStatement.
	 */
	public AssignmentStatement() {
		super();
	}
	
	public AssignmentStatement(ElementSymbol variable, Command value) {
        this.variable = variable;
        this.value = new ScalarSubquery(value);        
    }
	
    public AssignmentStatement(ElementSymbol variable, Expression value) {
        this.variable = variable;
        this.value = value;        
    }
 
    /**
     * @see #getExpression()
     */
    @Deprecated
    public Expression getValue() {
		return value;
	}

    /**
     * @see #setExpression(Expression)
     */
    @Deprecated
    public void setValue(Expression value) {
		this.value = value;
	}

    public Expression getExpression() {
    	return this.value;
    }
    
    public void setExpression(Expression expression) {
        this.value = expression;
    }
    
	/**
	 * Get the expression giving the value that is assigned to the variable.
	 * @return An <code>Expression</code> with the value
	 */
	public ElementSymbol getVariable() {
		return this.variable;
	}
	
	/**
	 * Set the variable that is assigned to the value
	 * @param<code>ElementSymbol</code> that is being assigned
	 */
	public void setVariable(ElementSymbol variable) {
		this.variable = variable;
	}	
	
	/**
	 * Return the type for this statement, this is one of the types
	 * defined on the statement object.
	 * @return The type of this statement
	 */
	public int getType() {
		return Statement.TYPE_ASSIGNMENT;
	}	

    // =========================================================================
    //                  P R O C E S S I N G     M E T H O D S
    // =========================================================================
        
    public void acceptVisitor(LanguageVisitor visitor) {
        visitor.visit(this);
    }

	/**
	 * Deep clone statement to produce a new identical statement.
	 * @return Deep clone 
	 */
	public Object clone() {
		AssignmentStatement clone = new AssignmentStatement((ElementSymbol) this.variable.clone(), (Expression) this.value.clone());
		return clone;
	}

    /**
     * Compare two AssignmentStatements for equality.  They will only evaluate to equal if
     * they are IDENTICAL: variable and its value which could be a command or an expression 
     * objects are equal.
     * @param obj Other object
     * @return True if equal
     */
    public boolean equals(Object obj) {
    	// Quick same object test
    	if(this == obj) {
    		return true;
		}

		// Quick fail tests		
    	if(!(obj instanceof AssignmentStatement)) {
    		return false;
		}

		AssignmentStatement other = (AssignmentStatement) obj;
		
        return 
    		// Compare the variables
    		EquivalenceUtil.areEqual(this.getVariable(), other.getVariable()) &&
            // Compare the values
    		EquivalenceUtil.areEqual(this.getExpression(), other.getExpression());
            // Compare the values
    }

    /**
     * Get hashcode for AssignmentStatement.  WARNING: This hash code relies on the hash codes
     * of the variable and its value which could be a command or an expression.
     * @return Hash code
     */
    public int hashCode() {
    	// This hash code relies on the variable and its value for this statement
    	// and criteria clauses, not on the from, order by, or option clauses
    	int myHash = 0;
    	myHash = HashCodeUtil.hashCode(myHash, this.getVariable());
    	myHash = HashCodeUtil.hashCode(myHash, this.getExpression());
		return myHash;
	}
    
    @Override
    public Class<?> getExpectedType() {
    	return getVariable().getType();
    }
    
      
} // END CLASS
