package io.openmessaging.benchmark.driver.tdengine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TDengineProducer {
    public TDengineProducer() {
        String jdbcUrl = "jdbc:TAOS://localhost:6030?user=root&password=taosdata";

        try {
            Connection conn = DriverManager.getConnection(jdbcUrl);
            System.out.println("===========Connected=================");
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
