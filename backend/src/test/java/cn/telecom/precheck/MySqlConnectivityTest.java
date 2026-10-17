package cn.telecom.precheck;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/** CI 使用真实 MySQL；本地无数据库时明确跳过，不用 H2 冒充 MySQL。 */
@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class MySqlConnectivityTest {
    @Autowired SqlSessionFactory factory;
    interface ProbeMapper { @Select("SELECT 1") int probe(); }
    @Test void myBatisExecutesQueryOnMySql() {
        factory.getConfiguration().addMapper(ProbeMapper.class);
        try (var session = factory.openSession()) {
            assertThat(session.getMapper(ProbeMapper.class).probe()).isEqualTo(1);
        }
    }
}
