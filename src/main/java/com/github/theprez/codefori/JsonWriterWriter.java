package com.github.theprez.codefori;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Stack;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.google.gson.stream.JsonWriter;
import com.ibm.as400.access.RecordFormat;

public class JsonWriterWriter implements Flushable, Closeable {
    enum ClosableDocumentElement {
        OBJECT_START, ARRAY_START;

        public void pop(JsonWriterWriter _w) throws IOException {
            switch (this) {
                case ARRAY_START:
                    _w.m_writer.endArray();
                    break;
                case OBJECT_START:
                    _w.m_writer.endObject();
                    break;
            }
            _w.m_writer.flush();
        }
    }

    private final Stack<ClosableDocumentElement> m_stack = new Stack<ClosableDocumentElement>();
    private JsonWriter m_writer;
    private AppLogger m_logger;

    public JsonWriterWriter(AppLogger _logger, JsonWriter _writer) {
        m_writer = _writer;
        m_logger = _logger;
    }

    public void startObject(String _name) throws IOException {
        m_writer.beginObject();
        if (StringUtils.isNonEmpty(_name)) {
            m_writer.name(_name);
        }
        m_stack.push(ClosableDocumentElement.OBJECT_START);
    }

    public void beginArray(String _arrayName) throws IOException {
        if (StringUtils.isNonEmpty(_arrayName)) {
            m_writer.name(_arrayName);
        }
        m_writer.beginArray();
        m_stack.push(ClosableDocumentElement.ARRAY_START);
    }

    public void addValue(String _prop, Object _value) throws IOException {
        if (null == _value) {
            m_writer.name(_prop).nullValue();
        } else if (_value instanceof CharSequence) {
            m_writer.name(_prop).value(_value.toString());
        } else if (_value instanceof Boolean) {
            m_writer.name(_prop).value((Boolean) _value);
        } else if (_value instanceof Number) {
            m_writer.name(_prop).value((Number) _value);
        } else {
            m_writer.name(_prop).value(_value.toString());
        }
    }

    public void addBeanObject(final String _name, final Object _bean) throws IOException {
        if (null == _bean) {
            return;
        }
        Class<?> c = _bean.getClass();
        LinkedHashMap<String, Object> p = new LinkedHashMap<String, Object>();
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() > 0) {
                continue;
            }
            String name = m.getName().toLowerCase();
            if (name.startsWith("is") || name.startsWith("get")) {
                try {
                    String prop = name.replaceFirst("^get", "");
                    Object val = m.invoke(_bean);
//                    if (!(val instanceof Class)) {
                        p.put(prop, val);
//                    }
                } catch (Exception e) {
                }
            }
        }
        addObject(_name, p);
    }

    public void err(Exception _e) {
        m_logger.printExceptionStack_verbose(_e);

        if (!m_stack.isEmpty() && ClosableDocumentElement.ARRAY_START == m_stack.peek()) {
            pop();
        }
        try {
            m_writer.name("error");
            startObject(null);
            addValue("message", _e.getLocalizedMessage());
            addValue("type", _e.getClass().getSimpleName());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            pop();
        }
    }

    private void pop() {
        ClosableDocumentElement pop = m_stack.pop();
        if (null != pop) {
            try {
                pop.pop(this);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public void endObject() {
        try {
            while (true) {
                ClosableDocumentElement pop = m_stack.pop();
                if (null == pop) {
                    return;
                }
                pop.pop(this);
                if (ClosableDocumentElement.OBJECT_START == pop) {
                    return;
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void endArray() throws IOException {
        while (true) {
            ClosableDocumentElement pop = m_stack.pop();
            if (null == pop) {
                return;
            }
            pop.pop(this);
            if (ClosableDocumentElement.ARRAY_START == pop) {
                return;
            }
        }
    }

    public void addValue(String _prop, Boolean _b) throws IOException {
        m_writer.name(_prop).value(_b);
    }

    public void addArrayElement(Object value) throws IOException {
        if (null == value) {
            m_writer.nullValue();
        } else if (value instanceof CharSequence) {
            m_writer.value(value.toString());
        } else if (value instanceof Boolean) {
            m_writer.value((Boolean) value);
        } else if (value instanceof Number) {
            m_writer.value((Number) value);
        } else {
            m_writer.value(value.toString());
        }
    }

    public void completeflush() {
        try {
            popAll();
            m_writer.flush();
        } catch (IOException e) {
            m_logger.exception(e);
        }
    }

    public void flush() throws IOException {
        m_writer.flush();
    }

    private void popAll() throws IOException {
        while (!m_stack.isEmpty()) {
            m_stack.pop().pop(this);
        }
    }

    @Override
    public void close() throws IOException {
        completeflush();
    }

    public void addObject(String _name, Map<String, Object> _colInfo) throws IOException {
        startObject(_name);
        try {
            for (Entry<String, Object> entry : _colInfo.entrySet()) {
                String prop = entry.getKey();
                Object value = entry.getValue();
                if (null == value) {
                    m_writer.name(prop).nullValue();
                } else if (value instanceof CharSequence) {
                    m_writer.name(prop).value(value.toString());
                } else if (value instanceof Boolean) {
                    m_writer.name(prop).value((Boolean) value);
                } else if (value instanceof Number) {
                    m_writer.name(prop).value((Number) value);
                } else {
                    m_writer.name(prop).value(value.toString());
                }
            }
        } finally {
            endObject();
        }
    }
}
