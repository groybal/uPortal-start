/**
 * Copyright 2007 The JA-SIG Collaborative.  All rights reserved.
 * See license distributed with this file and
 * available online at http://www.uportal.org/license.html
 */
package org.jasig.portal.tools.dbloader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Generates and executes SQL INSERT statements as the data XML document is
 * parsed.
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public class DataXmlHandler extends BaseDbXmlHandler {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final List<String> script = new LinkedList<String>();
    
    public DataXmlHandler(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }
    
    public List<String> getScript() {
        return this.script;
    }
    
    private String currentTable = null;
    private String currentColumn = null;
    private String currentValue = null;
    private Map<String, String> rowData;

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        if ("row".equals(name)) {
            this.rowData = new LinkedHashMap<String, String>();
        }
        
        this.chars = new StringBuilder();
    }
    
    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        if ("name".equals(name)) {
            final String itemName = this.chars.toString().trim();
            
            if (this.currentTable == null) {
                this.currentTable = itemName;
            }
            else if (this.currentColumn == null) {
                this.currentColumn = itemName;
            }
        }
        else if ("value".equals(name)) {
            this.currentValue = this.chars.toString().trim();
        }
        else if ("column".equals(name)) {
            this.rowData.put(this.currentColumn, this.currentValue);
            this.currentColumn = null;
            this.currentValue = null;
        }
        else if ("row".equals(name)) {
            this.doInsert();
            this.rowData = null;
        }
        else if ("table".equals(name)) {
            this.currentTable = null;
        }
        
        this.chars = null;
    }
    
    protected final void doInsert() {
        if (this.rowData.size() == 0) {
            this.logger.warn("Found a row with no data for table " + this.currentTable + ", the row will be ignored");
            return;
        }
        
        final StringBuilder columns = new StringBuilder();
        final StringBuilder parameters = new StringBuilder();
        final List<Object> values = new ArrayList<Object>(this.rowData.size());
        
        for (final Iterator<Entry<String, String>> rowIterator = this.rowData.entrySet().iterator(); rowIterator.hasNext(); ) {
            final Entry<String, String> row = rowIterator.next();
            columns.append(row.getKey());
            parameters.append("?");
            values.add(row.getValue());
            
            if (rowIterator.hasNext()) {
                columns.append(", ");
                parameters.append(", ");
            }
        }
        
        final String sql = "INSERT INTO " + this.currentTable + " (" + columns + ") VALUES (" + parameters + ")";
        this.logger.info(sql + "\t" + values);

        this.transactionTemplate.execute(new TransactionCallback() {

            /* (non-Javadoc)
             * @see org.springframework.transaction.support.TransactionCallback#doInTransaction(org.springframework.transaction.TransactionStatus)
             */
            public Object doInTransaction(TransactionStatus status) {
                return jdbcTemplate.update(sql, values.toArray());
            }
        });
    }
}