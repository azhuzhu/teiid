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

package org.teiid.translator.infinispan.dsl.metadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.teiid.metadata.BaseColumn.NullType;
import org.teiid.metadata.*;
import org.teiid.metadata.Column.SearchType;
import org.teiid.query.eval.TeiidScriptEngine;
import org.teiid.translator.MetadataProcessor;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.TypeFacility;
import org.teiid.translator.infinispan.dsl.InfinispanConnection;
import org.teiid.translator.infinispan.dsl.InfinispanPlugin;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;


/**
 * The ProtobufMetadataProcess is the logic for providing metadata to the translator based on
 * the google protobuf Descriptors and the defined class types.
 * 
 * <p>
 * Here are the rules that are being followed.
 * </p>
 * <li>Cache</li>
 * Each cache defined by the connection will be processed to create 1 or more tables.
 * <li>Table</li>
 * Each class is mapped to a table.
 * </br>
 * <li>Attributes</li>
 * Get/Is methods will be become table columns.
 * </br>
 * <li>One-to-one association</li>
 * The classes on both sides will be merged into a one table.  The nameInSource for the attributes in the class
 * contained within the root class will be assigned based on this format:  "get{Method}" name + "." + attribute
 * </br>
 * Example:   Person contains Contact (getContact)  and Contact has getter method getPhoneNumber  
 *     so the nameInSource for the column would become Contact.PhoneNumber
 * </br>
 * <li>One-to-many association</li>
 * The classes on both sides are mapped to two tables.  The root class will have primary key created.  The
 * table for the "many" side will have a foreign created. 
 * </br>
 * When an attribute is included from the many side (children), the total number of rows will be multiplied by
 * by the number of children objects.
 * <p>
 * <b>Decisions</b>
 * <li>User will need to decide what will be the primary key.  Will the key to the cache or a real attribute on the root class
 * will be used as the primary key.</li>
 * 
 */
public class ProtobufMetadataProcessor implements MetadataProcessor<InfinispanConnection>{
	public static final String URI="{http://www.teiid.org/translator/infinispan/2014}"; //$NON-NLS-1$
	public static final String PREFIX="teiid_infinispan";

	public static final String KEY_ASSOSIATED_WITH_FOREIGN_TABLE = "assosiated_with_table";  //$NON-NLS-1$
	
	/* The entity class name is needed for updates */
	@ExtensionMetadataProperty(applicable={Table.class}, datatype=String.class, display="Entity Class Name", description="Class Name for Entity in Cache", required=true)
    public static final String ENTITYCLASS= URI + "entity_class";
	
	public static final String GET = "get"; //$NON-NLS-1$
	public static final String IS = "is"; //$NON-NLS-1$
		
	public static final String VIEWTABLE_SUFFIX = "View"; //$NON-NLS-1$
	public static final String OBJECT_COL_SUFFIX = "Object"; //$NON-NLS-1$
	
	private TeiidScriptEngine engine = new TeiidScriptEngine();
	/* contains all the methods for each registered class in the cache */
	private Map<String, Method> methods = new  HashMap<String, Method>();
	@SuppressWarnings("rawtypes")
	private List<Class> registeredClasses;


	protected boolean isUpdatable = false;
	
	@SuppressWarnings("rawtypes")
	@Override
	public void process(MetadataFactory metadataFactory, InfinispanConnection conn) throws TranslatorException {
		registeredClasses = conn.getRegisteredClasses();
		for (Class c:registeredClasses) {
			try {
				methods.putAll( engine.getMethodMap(c) );
			} catch (ScriptException e) {
				throw new TranslatorException(e);
			}			
		}
			
		Map<String, Class<?>> cacheTypes = conn.getCacheNameClassTypeMapping();
		for (String cacheName : cacheTypes.keySet()) {

			Class<?> type = cacheTypes.get(cacheName);
			String pkField = conn.getPkField(cacheName);
			createRootTable(metadataFactory, type, conn.getDescriptor(cacheName), cacheName, pkField);
		}
		
	}

	private Table createRootTable(MetadataFactory mf, Class<?> entity, Descriptor descriptor, String cacheName, String pkField) throws TranslatorException {
			
		Table rootTable = addTable(mf, entity, descriptor);
		rootTable.setNameInSource(cacheName); 
		
	    Method pkMethod = null;
	    if (pkField != null) {
            pkMethod = methods.get(pkField);
	    }
	    
		addRootColumn(mf, Object.class, null, null, SearchType.Unsearchable, rootTable.getName(), rootTable,true); //$NON-NLS-1$
		
		processDescriptor(mf, descriptor.getFields(), rootTable, pkMethod);
		
		// add the PK column, if it doesnt exist
		if (pkField != null) {
			// if there is no method, need to create a column for the pkey
		    if (pkMethod == null) {
	            
		    	if (rootTable.getColumnByName(pkField) == null) {
	 	                      
		            // add a column so the PKey can be created, but make it not selectable
		    		addRootColumn(mf, entity, pkField, pkField, SearchType.Searchable, rootTable.getName(), rootTable, false);
		    	}
	
	         } else {
	 			// warn if no pk is defined
	 //			LogManager.logWarning(LogConstants.CTX_CONNECTOR, InfinispanPlugin.Util.gs(InfinispanPlugin.Event.TEIID21000, tableName));				
	         }	
			String pkName = "PK_" + pkField.toUpperCase(); //$NON-NLS-1$
            ArrayList<String> x = new ArrayList<String>(1) ;
            x.add(pkField);
            mf.addPrimaryKey(pkName, x , rootTable);

		}
			
		return rootTable;
	}
	
	private Table addTable(MetadataFactory mf, Class<?> entity, Descriptor descriptor) {
		String tName = getTableName(descriptor);
		Table t = mf.getSchema().getTable(tName);
		if (t != null) {
			//already loaded
			return t;
		}
		t = mf.addTable(tName);
		t.setSupportsUpdate(isUpdateable());

		t.setProperty(ENTITYCLASS, entity.getName());	
		return t;
		
	}
	private void processDescriptor(MetadataFactory mf, List<FieldDescriptor> fields, Table rootTable, Method pkMethod) throws TranslatorException {

		for (FieldDescriptor fd:fields) {	
			if (fd.isRepeated() ) {
				// Need to find the method name that corresponds to the repeating attribute
				// so that the actual method name can be used in defining the NIS
				// which will provide the correct method to use when retrieving the data at execution time
				String mName = findRepeatedMethodName(fd.getName());
				
				if (mName == null) {
					final String msg = InfinispanPlugin.Util
							.getString("ProtobufMetadataProcessor.noCorrespondingMethod", new Object[] { fd.getName() }); //$NON-NLS-1$ //$NON-NLS-2$
					throw new TranslatorException(msg);

				}
				processRepeatedType(mf,fd, mName, rootTable, pkMethod);	
			} else {
				addRootColumn(mf, getJavaType(fd), fd, SearchType.Searchable, rootTable.getName(), rootTable, true);	
			}
		}	
		
	}	
	
    private  String findRepeatedMethodName( String methodName) {
        if (methodName == null || methodName.length() == 0) {
            return null;
        }
        
        // because the class 'methods' contains 2 different references
        //  get'Name'  and 'Name', this will look for the 'Name' version
        for (Iterator it=methods.keySet().iterator(); it.hasNext();) {
        	String mName = (String) it.next();
        	if (mName.toLowerCase().startsWith(methodName.toLowerCase()) ) {
        		Method m = methods.get(mName);
        		Class<?> c = m.getReturnType();
        		if (Collection.class.isAssignableFrom(m.getReturnType()) || m.getReturnType().isArray()) {
        			return mName;
        		}
        	} 
        }
        return null;
    }	
	
	private void processRepeatedType(MetadataFactory mf, FieldDescriptor fd, String mName, Table rootTable, Method pkMethod) throws TranslatorException  {
		Descriptor d = fd.getMessageType();
		
		Class c = getRegisteredClass(fd.getMessageType().getName());
		
		Table t = addTable(mf, c, d);
		t.setNameInSource(rootTable.getNameInSource()); 
			
		List<FieldDescriptor> fields = fd.getMessageType().getFields();
		for (FieldDescriptor f:fields) {

			// need to use the repeated descriptor, fd, as the prefix to the NIS in order to perform query
			addSubColumn(mf, getJavaType(f), f, SearchType.Searchable, fd.getName(), t, true);	
		}
		
		if (pkMethod != null) {
			String methodName = pkMethod.getName().substring( GET.length());
			List<String> keyColumns = new ArrayList<String>();
			keyColumns.add(methodName);
			List<String> referencedKeyColumns = new ArrayList<String>();
			referencedKeyColumns.add(methodName);
			String fkName = "FK_" + rootTable.getName().toUpperCase();
    		addRootColumn(mf, pkMethod.getReturnType(), methodName, methodName, SearchType.Unsearchable, t.getName(), t, false);
			ForeignKey fk = mf.addForiegnKey(fkName, keyColumns, referencedKeyColumns, rootTable.getName(), t);
			fk.setNameInSource(mName);

		}
	}	
	
	private String getTableName(Descriptor descriptor) {
		return descriptor.getName();
	}
	
	private Class<?> getRegisteredClass(String name) throws TranslatorException {
		for (Class<?> c:registeredClasses) {
			if (c.getName().endsWith(name)) {
				return c;
			}
		}
		
		throw new TranslatorException("No registered marshall class had a name of " + name);
	}	
	
	/**
	 * @return boolean
	 */
	private boolean isUpdateable() {
		return this.isUpdatable;
	}

	private Column addRootColumn(MetadataFactory mf, Class<?> type, FieldDescriptor fd,
			 SearchType searchType, String entityName, Table rootTable, boolean selectable) {
		
		return addRootColumn(mf, type, fd.getFullName(), fd.getName(), searchType, entityName, rootTable, selectable);

	}
	private Column addRootColumn(MetadataFactory mf, Class<?> type, String columnFullName, String columnName,
			 SearchType searchType, String entityName, Table rootTable, boolean selectable) {
		String attributeName;
		String nis;
		
		// Null fd indicates this is for the object
		if (columnFullName != null) {
//			String fn = fd.getFullName();
			int idx;
			
			// the following logic is to set the NameInSource to the attribute name, for 
			// the root object.   For attributes that are in subclasses, then the
			// NameInSource will start with the method to access the subclass values.
			// QuickStart example:  
			//     root:    quickstart.Person.name   becomes  name
			// subclass:    quickstart.Person.PhoneNumber.number  becomes PhoneNumber.number
			//
			// The reflection logic, when multiple node names are used, will traverse the root
			// object by calling getPhoneNumber() to get its value, then will call getNumber()
			// to arrive at the value to return in the result set.
			if (rootTable.getName().equalsIgnoreCase(entityName)) {
				nis = columnName;
			} else {
				idx = columnFullName.indexOf(entityName);
				nis = columnFullName.substring(idx);
			}

			attributeName = columnName;
		} else {
			attributeName = entityName + OBJECT_COL_SUFFIX;
			nis = "this";
		}
	
		return addColumn(mf, type, attributeName, nis, searchType, rootTable, selectable);

	}
	
	private Column addSubColumn(MetadataFactory mf, Class<?> type, FieldDescriptor fd,
			 SearchType searchType, String nisPrefix, Table rootTable, boolean selectable) {
		String attributeName = fd.getName();
		String nis = nisPrefix + "." + fd.getName();

		return addColumn(mf, type, attributeName, nis, searchType, rootTable, selectable);

	}	
	
	private Column addColumn(MetadataFactory mf, Class<?> type, String attributeName, String nis, SearchType searchType, Table rootTable, boolean selectable) {
		Column c = mf.addColumn(attributeName, TypeFacility.getDataTypeName(TypeFacility.getRuntimeType(type)), rootTable);
		
		if (nis != null) {
			c.setNameInSource(nis);
		}
		
		c.setUpdatable(isUpdateable());
		c.setSearchType(searchType);
		c.setNativeType(type.getName());
		c.setSelectable(selectable);


		if (type.isPrimitive()) {
			c.setNullType(NullType.No_Nulls);
		}
		return c;
	}	
	
	
	private Class<?> getJavaType(FieldDescriptor fd) {
		
		   switch (fd.getJavaType()) {
		      case INT:         return Integer.class   ; 
		      case LONG:        return Long.class      ; 
		      case FLOAT:       return Float.class     ; 
		      case DOUBLE:      return Double.class    ; 
		      case BOOLEAN:     return Boolean.class   ; 
		      case STRING:      return String.class    ; 
		      case BYTE_STRING: return String.class    ; 
		      case ENUM:  		return String.class 	;
		      case MESSAGE:  	return String.class 	;
		      default:
		      	
		        return String.class;
		   }
	}
	
}