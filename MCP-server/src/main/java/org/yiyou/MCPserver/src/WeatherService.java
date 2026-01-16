package org.yiyou.MCPserver.src;

/**
 * 天气服务类，提供基于美国国家气象局API的天气预报和警报信息获取功能。
 * 该服务使用RestClient进行HTTP请求，并提供解析响应数据的方法。
 * 
 * 功能特点：
 * - 支持通过经纬度获取天气预报
 * - 支持查询特定地区的天气警报
 * - 使用Java记录类(Records)解析JSON响应
 * - 基于Spring框架的@Service组件
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 类级注释：定义了用于解析天气API响应的嵌套记录类
 * 包含Points、Forecast、Alert等顶层响应结构
 * 每个记录类都使用@JsonIgnoreProperties忽略未知字段，确保JSON反序列化兼容性
 */

@Service
public class WeatherService {

    /**
     * API基础URL（美国国家气象局）
     * 提供天气预报和警报数据的标准接口地址
     */
    private static final String BASE_URL = "https://api.weather.gov";

    /**
     * HTTP客户端实例
     * 用于发送所有天气相关的API请求
     * 初始化时配置了基础URL和默认请求头
     */
    private final RestClient restClient;

    /**
     * 构造函数，初始化RestClient实例。
     * 配置了基础URL、接受的响应类型和用户代理信息。
     * 
     * 默认请求头设置：
     * - Accept: application/geo+json 接受GeoJSON格式响应
     * - User-Agent: 客户端标识，用于API调用统计和联系
     */
    public WeatherService() {

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/geo+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (your@email.com)")
                .build();
    }

    /**
     * 响应结构定义：用于解析位置点信息
     * 包括地理位置属性和对应的预报URL
     * 
     * 内部类结构：
     * - Points: 顶层记录类，包含属性对象
     * - Props: 属性记录类，包含预报URL
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Points(@JsonProperty("properties") Props properties) {
        @JsonIgnoreProperties(ignoreUnknown = true)
            public record Props(@JsonProperty("forecast") String forecast) {
        }
    }

    /**
     * 响应结构定义：用于解析天气预报数据
     * 包含多个时间段的详细天气信息
     * 
     * 主要组成部分：
     * - Forecast: 顶层记录类，包含属性对象
     * - Props: 属性记录类，包含时间段列表
     * - Period: 时间段记录类，包含具体的天气指标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(@JsonProperty("properties") Props properties) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Props(@JsonProperty("periods") List<Period> periods) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Period(@JsonProperty("number") Integer number, @JsonProperty("name") String name,
                             @JsonProperty("startTime") String startTime, @JsonProperty("endTime") String endTime,
                             @JsonProperty("isDaytime") Boolean isDayTime, @JsonProperty("temperature") Integer temperature,
                             @JsonProperty("temperatureUnit") String temperatureUnit,
                             @JsonProperty("temperatureTrend") String temperatureTrend,
                             @JsonProperty("probabilityOfPrecipitation") Map probabilityOfPrecipitation,
                             @JsonProperty("windSpeed") String windSpeed, @JsonProperty("windDirection") String windDirection,
                             @JsonProperty("icon") String icon, @JsonProperty("shortForecast") String shortForecast,
                             @JsonProperty("detailedForecast") String detailedForecast) {
        }
    }

    /**
     * 响应结构定义：用于解析天气警报数据
     * 包含警报特征和详细属性信息
     * 
     * 结构说明：
     * - Alert: 顶层记录类，包含特征列表
     * - Feature: 特征记录类，包含警报属性
     * - Properties: 属性记录类，包含具体的警报详情
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(@JsonProperty("features") List<Feature> features) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Feature(@JsonProperty("properties") Properties properties) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Properties(@JsonProperty("event") String event, @JsonProperty("areaDesc") String areaDesc,
                                 @JsonProperty("severity") String severity, @JsonProperty("description") String description,
                                 @JsonProperty("instruction") String instruction) {
        }
    }

    /**
     * 获取特定纬度的预报
     * @param latitude Latitude
     * @param longitude Longitude
     * @return The forecast for the given location
     * @throws RestClientException if the request fails
     */
    @Tool(description = "获取特定纬度/经度的天气预报")
    public String getWeatherForecastByLocation(double latitude, double longitude) {

        // 第一步：获取位置信息
        // 调用API获取给定经纬度的位置数据
        var points = restClient.get()
                .uri("/points/{latitude},{longitude}", latitude, longitude)
                .retrieve()
                .body(Points.class);

        // 第二步：获取天气预报数据
        // 使用从位置信息中获取的预报URL获取具体预报数据
        var forecast = restClient.get().uri(points.properties().forecast()).retrieve().body(Forecast.class);

        String forecastText = forecast.properties().periods().stream().map(p -> {
            return String.format("""
					%s:
					Temperature: %s %s
					Wind: %s %s
					Forecast: %s
					""", p.name(), p.temperature(), p.temperatureUnit(), p.windSpeed(), p.windDirection(),
                    p.detailedForecast());
        }).collect(Collectors.joining());

        return forecastText;
    }

    /**
     * 获取特定区域的警报
     * @param state Area code. Two-letter US state code (e.g. CA, NY)
     * @return Human readable alert information
     * @throws RestClientException if the request fails
     */
    @Tool(description = "获取美国各州的天气警报。输入是两个字母的美国州代码（例如：CA,NY）")
    public String getAlerts(String state) {
        // 第一步：获取警报数据
        // 调用API获取指定地区的活跃警报信息
        Alert alert = restClient.get().uri("/alerts/active/area/{state}", state).retrieve().body(Alert.class);

        return alert.features()
                .stream()
                .map(f -> String.format("""
					Event: %s
					Area: %s
					Severity: %s
					Description: %s
					Instructions: %s
					""", f.properties().event(), f.properties.areaDesc(), f.properties.severity(),
                        f.properties.description(), f.properties.instruction()))
                .collect(Collectors.joining("\n"));
    }

}