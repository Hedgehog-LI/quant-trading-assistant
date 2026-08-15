package com.quant.trade.marketdata.poc;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-01 架构防回归测试（TEST-01）。普通 JUnit5 单元测试，不启动 Spring 上下文，
 * 用 java.nio 扫描源码树 + 反射/DOM 解析做静态断言（AGENTS.md：MyBatis SQL 写在
 * src/main/resources/mapper/*.xml，marketdata.poc 包不得使用注解 SQL）：
 * 其一，主代码 poc 包递归全部 .java 中 MyBatis CRUD 注解出现计数为 0（覆盖 Mr0PocMapper
 * 与 Mr0PocAnalysisMapper 及包内未来新增类）；其二，Mr0PocAnalysisMapper.xml 的 statement
 * id 集合与 Mr0PocAnalysisMapper 接口方法名集合完全相等（恰 6 个）。
 * 路径解析从模块根（工作目录）出发，向上回溯定位 pom.xml，兼容 Maven surefire 工作目录。
 */
class Mr0PocMapperXmlArchitectureTest {

    /** 匹配 MyBatis CRUD 注解的使用点（含 import 后的使用与注释中的字面量，从严计数）。 */
    private static final Pattern MYBATIS_CRUD_ANNOTATION = Pattern.compile("@(Select|Insert|Update|Delete)\\b");

    private static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");

    @Test
    void pocMainPackageContainsNoMybatisAnnotationSql() throws Exception {
        Path pocMainJava = moduleRoot().resolve("src/main/java/com/quant/trade/marketdata/poc");
        assertThat(Files.isDirectory(pocMainJava)).as("主代码 poc 包目录必须存在: %s", pocMainJava).isTrue();

        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(pocMainJava)) {
            stream.filter(path -> path.toString().endsWith(".java")).sorted().forEach(javaFiles::add);
        }
        assertThat(javaFiles).as("主代码 poc 包必须包含 mapper 源文件").isNotEmpty();

        Map<String, Integer> annotationCounts = new LinkedHashMap<>();
        for (Path file : javaFiles) {
            Matcher matcher = MYBATIS_CRUD_ANNOTATION.matcher(Files.readString(file));
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            if (count > 0) {
                annotationCounts.put(pocMainJava.relativize(file).toString(), count);
            }
        }
        assertThat(annotationCounts)
                .as("marketdata.poc 包主代码不允许 MyBatis 注解 SQL（含 Mr0PocMapper/Mr0PocAnalysisMapper），违例文件->计数")
                .isEmpty();
    }

    @Test
    void analysisMapperXmlStatementIdsMatchInterfaceMethods() throws Exception {
        Path mapperXml = moduleRoot().resolve("src/main/resources/mapper/Mr0PocAnalysisMapper.xml");
        assertThat(Files.isRegularFile(mapperXml)).as("Mr0PocAnalysisMapper.xml 必须存在: %s", mapperXml).isTrue();

        Set<String> methodNames = Arrays.stream(Mr0PocAnalysisMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(methodNames).as("接口方法名（反射声明方法）").hasSize(6);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        // 离线解析：mapper XML 头部 DOCTYPE 指向外部 mybatis-3-mapper.dtd，禁止解析器联网抓取
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        Document document = documentBuilder.parse(new File(mapperXml.toUri()));
        Element root = document.getDocumentElement();
        assertThat(root.getTagName()).isEqualTo("mapper");
        assertThat(root.getAttribute("namespace"))
                .as("XML namespace 必须绑定 Mr0PocAnalysisMapper 接口全限定名")
                .isEqualTo(Mr0PocAnalysisMapper.class.getName());

        Set<String> statementIds = new TreeSet<>();
        NodeList statements = root.getChildNodes();
        for (int i = 0; i < statements.getLength(); i++) {
            if (statements.item(i) instanceof Element element && STATEMENT_TAGS.contains(element.getTagName())) {
                assertThat(element.getAttribute("id")).as("statement 必须声明非空 id").isNotBlank();
                statementIds.add(element.getAttribute("id"));
            }
        }
        assertThat(statementIds)
                .as("Mr0PocAnalysisMapper.xml statement id 集合必须等于接口方法名集合（恰 6 个）")
                .isEqualTo(methodNames);
    }

    /** 从工作目录（surefire 默认为模块根）向上回溯定位含 pom.xml 与 src/main/java 的模块根。 */
    private static Path moduleRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("src/main/java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("无法从工作目录定位模块根: " + Paths.get("").toAbsolutePath());
    }
}
