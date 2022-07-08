package io.openmessaging.benchmark.driver.tdengine;

import com.taosdata.jdbc.tmq.Deserializer;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class ValueDecoder implements Deserializer<Record> {

    @Override
    public void configure(Map<?, ?> configs) {
    }

    @Override
    public Record deserialize(ResultSet data) throws InstantiationException, IllegalAccessException, SQLException, IntrospectionException, InvocationTargetException {
        return new Record(data.getLong(1), data.getBytes(2));
    }

    @Override
    public void close() {
        Deserializer.super.close();
    }
}
