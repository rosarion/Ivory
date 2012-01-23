package ivory.regression.basic;

import static ivory.regression.RegressionUtils.loadScoresIntoMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import ivory.core.eval.Qrels;
import ivory.core.eval.RankedListEvaluator;
import ivory.smrf.retrieval.Accumulator;
import ivory.smrf.retrieval.BatchQueryRunner;

import java.util.Map;
import java.util.Set;

import junit.framework.JUnit4TestAdapter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;
import org.junit.Test;

import com.google.common.collect.Maps;

import edu.umd.cloud9.collection.DocnoMapping;

public class Robust04_Basic {
  private static final Logger LOG = Logger.getLogger(Robust04_Basic.class);

  private static String[] sDirBaseRawAP = new String[] {
    "601", "0.4648", "602", "0.2787", "603", "0.2931", "604", "0.8289", "605", "0.0758",
    "606", "0.4768", "607", "0.2038", "608", "0.0548", "609", "0.3040", "610", "0.0245",
    "611", "0.2730", "612", "0.4695", "613", "0.2278", "614", "0.2014", "615", "0.1071",
    "616", "0.7291", "617", "0.2573", "618", "0.2135", "619", "0.5546", "620", "0.0671",
    "621", "0.3175", "622", "0.0349", "623", "0.3311", "624", "0.2460", "625", "0.0247",
    "626", "0.1542", "627", "0.0140", "628", "0.2397", "629", "0.1319", "630", "0.6110",
    "631", "0.1560", "632", "0.2665", "633", "0.4968", "634", "0.7553", "635", "0.5210",
    "636", "0.1321", "637", "0.4508", "638", "0.0414", "639", "0.1334", "640", "0.3590",
    "641", "0.3169", "642", "0.1531", "643", "0.4792", "644", "0.2338", "645", "0.5992",
    "646", "0.3064", "647", "0.2310", "648", "0.1672", "649", "0.7222", "650", "0.0874",
    "651", "0.0574", "652", "0.3183", "653", "0.5799", "654", "0.4083", "655", "0.0014",
    "656", "0.5132", "657", "0.4083", "658", "0.1280", "659", "0.4606", "660", "0.6591",
    "661", "0.5919", "662", "0.6254", "663", "0.4044", "664", "0.3955", "665", "0.2048",
    "666", "0.0084", "667", "0.3518", "668", "0.3408", "669", "0.1557", "670", "0.1291",
    "671", "0.3049", "672", "0.0000", "673", "0.3175", "674", "0.1371", "675", "0.2941",
    "676", "0.2827", "677", "0.8928", "678", "0.2102", "679", "0.8833", "680", "0.0756",
    "681", "0.3877", "682", "0.2516", "683", "0.0273", "684", "0.0918", "685", "0.2809",
    "686", "0.2515", "687", "0.2149", "688", "0.1168", "689", "0.0060", "690", "0.0047",
    "691", "0.3403", "692", "0.4541", "693", "0.3279", "694", "0.4762", "695", "0.2949",
    "696", "0.2975", "697", "0.1600", "698", "0.4824", "699", "0.4594", "700", "0.4381" };

  private static String[] sDirBaseRawP10 = new String[] {
    "601", "0.3000", "602", "0.3000", "603", "0.2000", "604", "0.6000", "605", "0.1000",
    "606", "0.5000", "607", "0.3000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.5000", "612", "0.7000", "613", "0.5000", "614", "0.2000", "615", "0.2000",
    "616", "1.0000", "617", "0.5000", "618", "0.3000", "619", "0.7000", "620", "0.1000",
    "621", "0.7000", "622", "0.0000", "623", "0.6000", "624", "0.4000", "625", "0.1000",
    "626", "0.1000", "627", "0.1000", "628", "0.4000", "629", "0.2000", "630", "0.3000",
    "631", "0.2000", "632", "0.6000", "633", "1.0000", "634", "0.8000", "635", "0.8000",
    "636", "0.1000", "637", "0.7000", "638", "0.2000", "639", "0.2000", "640", "0.6000",
    "641", "0.5000", "642", "0.2000", "643", "0.4000", "644", "0.3000", "645", "0.9000",
    "646", "0.4000", "647", "0.6000", "648", "0.5000", "649", "1.0000", "650", "0.2000",
    "651", "0.2000", "652", "0.7000", "653", "0.6000", "654", "1.0000", "655", "0.0000",
    "656", "0.7000", "657", "0.6000", "658", "0.3000", "659", "0.5000", "660", "0.9000",
    "661", "0.8000", "662", "0.8000", "663", "0.5000", "664", "0.4000", "665", "0.4000",
    "666", "0.0000", "667", "0.8000", "668", "0.5000", "669", "0.2000", "670", "0.3000",
    "671", "0.5000", "672", "0.0000", "673", "0.5000", "674", "0.2000", "675", "0.5000",
    "676", "0.3000", "677", "0.8000", "678", "0.3000", "679", "0.6000", "680", "0.2000",
    "681", "0.5000", "682", "0.5000", "683", "0.3000", "684", "0.3000", "685", "0.4000",
    "686", "0.4000", "687", "0.7000", "688", "0.3000", "689", "0.0000", "690", "0.0000",
    "691", "0.5000", "692", "0.7000", "693", "0.3000", "694", "0.5000", "695", "0.9000",
    "696", "0.6000", "697", "0.3000", "698", "0.3000", "699", "0.7000", "700", "0.7000" };

  private static String[] sDirSDRawAP = new String[] {
    "601", "0.5135", "602", "0.2988", "603", "0.2670", "604", "0.8385", "605", "0.0657",
    "606", "0.5350", "607", "0.2260", "608", "0.0721", "609", "0.3058", "610", "0.0293",
    "611", "0.2704", "612", "0.4854", "613", "0.2473", "614", "0.2059", "615", "0.0774",
    "616", "0.7315", "617", "0.2576", "618", "0.2108", "619", "0.5605", "620", "0.0708",
    "621", "0.3581", "622", "0.0776", "623", "0.3116", "624", "0.2599", "625", "0.0250",
    "626", "0.1527", "627", "0.0188", "628", "0.2306", "629", "0.1923", "630", "0.7208",
    "631", "0.1752", "632", "0.2267", "633", "0.4989", "634", "0.7522", "635", "0.5822",
    "636", "0.1637", "637", "0.5026", "638", "0.0599", "639", "0.1473", "640", "0.3669",
    "641", "0.3038", "642", "0.1601", "643", "0.4822", "644", "0.2329", "645", "0.5984",
    "646", "0.3294", "647", "0.2360", "648", "0.3081", "649", "0.7009", "650", "0.0865",
    "651", "0.0537", "652", "0.3186", "653", "0.5825", "654", "0.4255", "655", "0.0012",
    "656", "0.5392", "657", "0.4574", "658", "0.1288", "659", "0.3330", "660", "0.6569",
    "661", "0.6015", "662", "0.6263", "663", "0.4571", "664", "0.4658", "665", "0.2169",
    "666", "0.0138", "667", "0.3590", "668", "0.3557", "669", "0.1582", "670", "0.1325",
    "671", "0.3708", "672", "0.0000", "673", "0.3167", "674", "0.1359", "675", "0.3263",
    "676", "0.2828", "677", "0.8723", "678", "0.2333", "679", "0.8972", "680", "0.0956",
    "681", "0.5040", "682", "0.2648", "683", "0.0237", "684", "0.1280", "685", "0.2288",
    "686", "0.2304", "687", "0.2306", "688", "0.1193", "689", "0.0121", "690", "0.0067",
    "691", "0.3528", "692", "0.4736", "693", "0.3257", "694", "0.4725", "695", "0.2682",
    "696", "0.2945", "697", "0.1693", "698", "0.4848", "699", "0.5331", "700", "0.5569" };

  private static String[] sDirSDRawP10 = new String[] {
    "601", "0.3000", "602", "0.3000", "603", "0.2000", "604", "0.6000", "605", "0.2000",
    "606", "0.5000", "607", "0.3000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.5000", "612", "0.6000", "613", "0.5000", "614", "0.3000", "615", "0.1000",
    "616", "0.9000", "617", "0.5000", "618", "0.3000", "619", "0.6000", "620", "0.1000",
    "621", "0.8000", "622", "0.1000", "623", "0.6000", "624", "0.4000", "625", "0.1000",
    "626", "0.1000", "627", "0.1000", "628", "0.4000", "629", "0.4000", "630", "0.3000",
    "631", "0.1000", "632", "0.6000", "633", "1.0000", "634", "0.8000", "635", "0.8000",
    "636", "0.3000", "637", "0.6000", "638", "0.2000", "639", "0.2000", "640", "0.6000",
    "641", "0.5000", "642", "0.2000", "643", "0.4000", "644", "0.2000", "645", "0.9000",
    "646", "0.4000", "647", "0.6000", "648", "0.6000", "649", "1.0000", "650", "0.2000",
    "651", "0.2000", "652", "0.7000", "653", "0.6000", "654", "1.0000", "655", "0.0000",
    "656", "0.7000", "657", "0.6000", "658", "0.3000", "659", "0.4000", "660", "0.9000",
    "661", "0.8000", "662", "0.8000", "663", "0.5000", "664", "0.6000", "665", "0.3000",
    "666", "0.0000", "667", "0.8000", "668", "0.5000", "669", "0.1000", "670", "0.3000",
    "671", "0.5000", "672", "0.0000", "673", "0.6000", "674", "0.1000", "675", "0.5000",
    "676", "0.2000", "677", "0.7000", "678", "0.4000", "679", "0.6000", "680", "0.3000",
    "681", "0.6000", "682", "0.5000", "683", "0.4000", "684", "0.3000", "685", "0.3000",
    "686", "0.4000", "687", "0.8000", "688", "0.5000", "689", "0.0000", "690", "0.0000",
    "691", "0.5000", "692", "0.7000", "693", "0.5000", "694", "0.5000", "695", "0.8000",
    "696", "0.6000", "697", "0.3000", "698", "0.3000", "699", "0.7000", "700", "0.7000" };

  private static String[] sDirFDRawAP = new String[] { 
    "601", "0.6646", "602", "0.2959", "603", "0.2887", "604", "0.8372", "605", "0.0675",
    "606", "0.5663", "607", "0.2200", "608", "0.0918", "609", "0.3163", "610", "0.0249",
    "611", "0.2672", "612", "0.4854", "613", "0.2481", "614", "0.2047", "615", "0.0611",
    "616", "0.7315", "617", "0.2430", "618", "0.2012", "619", "0.5651", "620", "0.0750",
    "621", "0.4290", "622", "0.0776", "623", "0.2936", "624", "0.2679", "625", "0.0253",
    "626", "0.1527", "627", "0.0150", "628", "0.2306", "629", "0.1604", "630", "0.7870",
    "631", "0.2420", "632", "0.2163", "633", "0.4989", "634", "0.7522", "635", "0.5746",
    "636", "0.1605", "637", "0.5373", "638", "0.0599", "639", "0.1423", "640", "0.3604",
    "641", "0.3075", "642", "0.1648", "643", "0.5010", "644", "0.2616", "645", "0.5984",
    "646", "0.3440", "647", "0.2360", "648", "0.2918", "649", "0.7009", "650", "0.0813",
    "651", "0.0537", "652", "0.3184", "653", "0.5750", "654", "0.4255", "655", "0.0012",
    "656", "0.5515", "657", "0.4457", "658", "0.1288", "659", "0.3589", "660", "0.6600",
    "661", "0.5970", "662", "0.6263", "663", "0.4663", "664", "0.5640", "665", "0.1965",
    "666", "0.0137", "667", "0.3586", "668", "0.3557", "669", "0.1582", "670", "0.1325",
    "671", "0.3678", "673", "0.3079", "672", "0.0000", "674", "0.1359", "675", "0.2996",
    "676", "0.2828", "677", "0.8840", "678", "0.2260", "679", "0.8972", "680", "0.0983",
    "681", "0.5156", "682", "0.2749", "683", "0.0237", "684", "0.1226", "685", "0.2444",
    "686", "0.2450", "687", "0.2947", "688", "0.1135", "689", "0.0121", "690", "0.0062",
    "691", "0.3413", "692", "0.4806", "693", "0.3385", "694", "0.4725", "695", "0.2460",
    "696", "0.3029", "697", "0.1785", "698", "0.4851", "699", "0.5331", "700", "0.5569" };

  private static String[] sDirFDRawP10 = new String[] {
    "601", "0.3000", "602", "0.2000", "603", "0.2000", "604", "0.6000", "605", "0.3000",
    "606", "0.5000", "607", "0.3000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.5000", "612", "0.6000", "613", "0.5000", "614", "0.3000", "615", "0.0000",
    "616", "0.9000", "617", "0.5000", "618", "0.3000", "619", "0.7000", "620", "0.1000",
    "621", "0.9000", "622", "0.1000", "623", "0.5000", "624", "0.4000", "625", "0.1000",
    "626", "0.1000", "627", "0.1000", "628", "0.4000", "629", "0.3000", "630", "0.3000",
    "631", "0.6000", "632", "0.7000", "633", "1.0000", "634", "0.8000", "635", "0.8000",
    "636", "0.2000", "637", "0.7000", "638", "0.2000", "639", "0.2000", "640", "0.6000",
    "641", "0.7000", "642", "0.2000", "643", "0.4000", "644", "0.3000", "645", "0.9000",
    "646", "0.4000", "647", "0.6000", "648", "0.6000", "649", "1.0000", "650", "0.1000",
    "651", "0.2000", "652", "0.7000", "653", "0.5000", "654", "1.0000", "655", "0.0000",
    "656", "0.7000", "657", "0.6000", "658", "0.3000", "659", "0.5000", "660", "0.9000",
    "661", "0.8000", "662", "0.8000", "663", "0.6000", "664", "0.6000", "665", "0.3000",
    "666", "0.0000", "667", "0.8000", "668", "0.5000", "669", "0.1000", "670", "0.3000",
    "671", "0.5000", "672", "0.0000", "673", "0.6000", "674", "0.1000", "675", "0.5000",
    "676", "0.2000", "677", "0.7000", "678", "0.4000", "679", "0.6000", "680", "0.3000",
    "681", "0.6000", "682", "0.6000", "683", "0.4000", "684", "0.3000", "685", "0.3000",
    "686", "0.3000", "687", "0.8000", "688", "0.5000", "689", "0.0000", "690", "0.0000",
    "691", "0.5000", "692", "0.7000", "693", "0.5000", "694", "0.5000", "695", "0.8000",
    "696", "0.7000", "697", "0.3000", "698", "0.3000", "699", "0.7000", "700", "0.7000" };

  private static String[] sBm25BaseRawAP = new String[] {
    "601", "0.5441", "602", "0.2755", "603", "0.3273", "604", "0.8168", "605", "0.0713",
    "606", "0.4982", "607", "0.1746", "608", "0.0645", "609", "0.3383", "610", "0.0170",
    "611", "0.2175", "612", "0.5672", "613", "0.1909", "614", "0.1817", "615", "0.0715",
    "616", "0.8164", "617", "0.2511", "618", "0.2063", "619", "0.5921", "620", "0.0799",
    "621", "0.3915", "622", "0.0512", "623", "0.2854", "624", "0.2576", "625", "0.0276",
    "626", "0.1267", "627", "0.0109", "628", "0.2449", "629", "0.1424", "630", "0.7024",
    "631", "0.1751", "632", "0.2144", "633", "0.5022", "634", "0.7553", "635", "0.5225",
    "636", "0.1364", "637", "0.4677", "638", "0.0375", "639", "0.1136", "640", "0.3195",
    "641", "0.3270", "642", "0.1531", "643", "0.4771", "644", "0.2765", "645", "0.6010",
    "646", "0.3262", "647", "0.2067", "648", "0.0824", "649", "0.7240", "650", "0.0986",
    "651", "0.0521", "652", "0.3200", "653", "0.5812", "654", "0.1926", "655", "0.0017",
    "656", "0.5236", "657", "0.3836", "658", "0.1365", "659", "0.2991", "660", "0.6603",
    "661", "0.6059", "662", "0.6554", "663", "0.4316", "664", "0.5192", "665", "0.2212",
    "666", "0.0060", "667", "0.3441", "668", "0.3811", "669", "0.1573", "670", "0.1019",
    "671", "0.3157", "672", "0.0000", "673", "0.2703", "674", "0.1413", "675", "0.2656",
    "676", "0.2868", "677", "0.9182", "678", "0.1751", "679", "0.8722", "680", "0.0615",
    "681", "0.1297", "682", "0.2353", "683", "0.0316", "684", "0.0000", "685", "0.3065",
    "686", "0.3040", "687", "0.2010", "688", "0.1059", "689", "0.0073", "690", "0.0046",
    "691", "0.3800", "692", "0.4351", "693", "0.3423", "694", "0.4735", "695", "0.3155",
    "696", "0.3306", "697", "0.1510", "698", "0.3768", "699", "0.4976", "700", "0.4617" };

  private static String[] sBm25BaseRawP10 = new String[] {
    "601", "0.3000", "602", "0.3000", "603", "0.5000", "604", "0.6000", "605", "0.2000",
    "606", "0.4000", "607", "0.3000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.3000", "612", "0.7000", "613", "0.2000", "614", "0.1000", "615", "0.1000",
    "616", "1.0000", "617", "0.6000", "618", "0.4000", "619", "0.8000", "620", "0.1000",
    "621", "0.8000", "622", "0.1000", "623", "0.6000", "624", "0.4000", "625", "0.1000",
    "626", "0.0000", "627", "0.1000", "628", "0.4000", "629", "0.2000", "630", "0.3000",
    "631", "0.1000", "632", "0.6000", "633", "1.0000", "634", "0.8000", "635", "0.8000",
    "636", "0.1000", "637", "0.8000", "638", "0.1000", "639", "0.2000", "640", "0.4000",
    "641", "0.5000", "642", "0.2000", "643", "0.4000", "644", "0.3000", "645", "0.9000",
    "646", "0.4000", "647", "0.5000", "648", "0.1000", "649", "1.0000", "650", "0.2000",
    "651", "0.2000", "652", "0.8000", "653", "0.6000", "654", "0.4000", "655", "0.0000",
    "656", "0.7000", "657", "0.5000", "658", "0.3000", "659", "0.2000", "660", "0.9000",
    "661", "0.8000", "662", "0.9000", "663", "0.5000", "664", "0.6000", "665", "0.3000",
    "666", "0.0000", "667", "0.7000", "668", "0.6000", "669", "0.1000", "670", "0.2000",
    "671", "0.5000", "672", "0.0000", "673", "0.5000", "674", "0.2000", "675", "0.3000",
    "676", "0.3000", "677", "0.8000", "678", "0.4000", "679", "0.6000", "680", "0.3000",
    "681", "0.4000", "682", "0.6000", "683", "0.2000", "684", "0.0000", "685", "0.4000",
    "686", "0.5000", "687", "0.8000", "688", "0.3000", "689", "0.0000", "690", "0.0000",
    "691", "0.5000", "692", "0.7000", "693", "0.6000", "694", "0.6000", "695", "0.9000",
    "696", "0.7000", "697", "0.3000", "698", "0.4000", "699", "0.7000", "700", "0.6000" };

  private static String[] sBm25SDRawAP = new String[] {
    "601", "0.3367", "602", "0.2826", "603", "0.3152", "604", "0.8482", "605", "0.0688",
    "606", "0.5647", "607", "0.2388", "608", "0.0832", "609", "0.3292", "610", "0.0262",
    "611", "0.2474", "612", "0.5062", "613", "0.2137", "614", "0.1817", "615", "0.0556",
    "616", "0.8186", "617", "0.2597", "618", "0.2128", "619", "0.5627", "620", "0.0333",
    "621", "0.4792", "622", "0.2646", "623", "0.2387", "624", "0.2885", "625", "0.0275",
    "626", "0.1257", "627", "0.0186", "628", "0.1838", "629", "0.1848", "630", "0.7794",
    "631", "0.1962", "632", "0.1719", "633", "0.5117", "634", "0.7259", "635", "0.5711",
    "636", "0.2654", "637", "0.5681", "638", "0.0624", "639", "0.1424", "640", "0.3534",
    "641", "0.2754", "642", "0.1586", "643", "0.4527", "644", "0.2990", "645", "0.5870",
    "646", "0.3925", "647", "0.2233", "648", "0.2946", "649", "0.6826", "650", "0.0900",
    "651", "0.0585", "652", "0.3195", "653", "0.5636", "654", "0.3396", "655", "0.0014",
    "656", "0.5427", "657", "0.4305", "658", "0.1467", "659", "0.2628", "660", "0.6435",
    "661", "0.6096", "662", "0.6554", "663", "0.4750", "664", "0.6198", "665", "0.2055",
    "666", "0.0143", "667", "0.3664", "668", "0.3592", "669", "0.1626", "670", "0.1100",
    "671", "0.4210", "672", "0.0000", "673", "0.2742", "674", "0.1413", "675", "0.3369",
    "676", "0.2855", "677", "0.8809", "678", "0.3005", "679", "0.7579", "680", "0.0653",
    "681", "0.4063", "682", "0.2378", "683", "0.0300", "684", "0.1012", "685", "0.2813",
    "686", "0.2550", "687", "0.2472", "688", "0.1162", "689", "0.0185", "690", "0.0097",
    "691", "0.3823", "692", "0.4531", "693", "0.2793", "694", "0.4547", "695", "0.2756",
    "696", "0.3198", "697", "0.1765", "698", "0.3823", "699", "0.5651", "700", "0.6554" };

  private static String[] sBm25SDRawP10 = new String[] {
    "601", "0.4000", "602", "0.3000", "603", "0.4000", "604", "0.6000", "605", "0.2000",
    "606", "0.4000", "607", "0.4000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.5000", "612", "0.6000", "613", "0.2000", "614", "0.1000", "615", "0.0000",
    "616", "1.0000", "617", "0.7000", "618", "0.3000", "619", "0.7000", "620", "0.0000",
    "621", "0.8000", "622", "0.4000", "623", "0.5000", "624", "0.4000", "625", "0.1000",
    "626", "0.0000", "627", "0.1000", "628", "0.4000", "629", "0.2000", "630", "0.3000",
    "631", "0.1000", "632", "0.5000", "633", "1.0000", "634", "0.8000", "635", "0.7000",
    "636", "0.4000", "637", "0.7000", "638", "0.3000", "639", "0.4000", "640", "0.5000",
    "641", "0.5000", "642", "0.3000", "643", "0.4000", "644", "0.3000", "645", "0.9000",
    "646", "0.4000", "647", "0.6000", "648", "0.7000", "649", "1.0000", "650", "0.1000",
    "651", "0.1000", "652", "0.8000", "653", "0.6000", "654", "0.9000", "655", "0.0000",
    "656", "0.7000", "657", "0.5000", "658", "0.3000", "659", "0.2000", "660", "0.9000",
    "661", "0.9000", "662", "0.9000", "663", "0.6000", "664", "0.7000", "665", "0.3000",
    "666", "0.0000", "667", "0.8000", "668", "0.5000", "669", "0.1000", "670", "0.3000",
    "671", "0.5000", "672", "0.0000", "673", "0.5000", "674", "0.2000", "675", "0.6000",
    "676", "0.2000", "677", "0.7000", "678", "0.5000", "679", "0.6000", "680", "0.2000",
    "681", "0.5000", "682", "0.7000", "683", "0.4000", "684", "0.2000", "685", "0.4000",
    "686", "0.4000", "687", "0.7000", "688", "0.3000", "689", "0.0000", "690", "0.0000",
    "691", "0.4000", "692", "0.6000", "693", "0.5000", "694", "0.5000", "695", "0.8000",
    "696", "0.7000", "697", "0.3000", "698", "0.3000", "699", "0.7000", "700", "0.8000" };

  private static String[] sBm25FDRawAP = new String[] {
    "601", "0.6167", "602", "0.2798", "603", "0.3230", "604", "0.8317", "605", "0.0778",
    "606", "0.5517", "607", "0.2029", "608", "0.0973", "609", "0.3458", "610", "0.0204",
    "611", "0.2237", "612", "0.5359", "613", "0.2087", "614", "0.1817", "615", "0.0593",
    "616", "0.8133", "617", "0.2437", "618", "0.2108", "619", "0.5797", "620", "0.0434",
    "621", "0.5115", "622", "0.1996", "623", "0.2198", "624", "0.2874", "625", "0.0261",
    "626", "0.1257", "627", "0.0142", "628", "0.2051", "629", "0.1523", "630", "0.7750",
    "631", "0.2249", "632", "0.1956", "633", "0.5069", "634", "0.7259", "635", "0.5427",
    "636", "0.2293", "637", "0.5653", "638", "0.0565", "639", "0.1202", "640", "0.3372",
    "641", "0.3017", "642", "0.1606", "643", "0.4745", "644", "0.3150", "645", "0.6050",
    "646", "0.3708", "647", "0.2190", "648", "0.2102", "649", "0.7001", "650", "0.0869",
    "651", "0.0627", "652", "0.3195", "653", "0.5716", "654", "0.3061", "655", "0.0015",
    "656", "0.5206", "657", "0.4070", "658", "0.1406", "659", "0.3341", "660", "0.6625",
    "661", "0.6062", "662", "0.6554", "663", "0.4436", "664", "0.6615", "665", "0.2126",
    "666", "0.0098", "667", "0.3461", "668", "0.3796", "669", "0.1631", "670", "0.1082",
    "671", "0.3778", "672", "0.0000", "673", "0.2648", "674", "0.1413", "675", "0.2689",
    "676", "0.2895", "677", "0.8888", "678", "0.2651", "679", "0.7802", "680", "0.0740",
    "681", "0.2485", "682", "0.2308", "683", "0.0286", "684", "0.0752", "685", "0.2978",
    "686", "0.2820", "687", "0.3280", "688", "0.1117", "689", "0.0169", "690", "0.0065",
    "691", "0.3609", "692", "0.4630", "693", "0.3307", "694", "0.4590", "695", "0.2644",
    "696", "0.3306", "697", "0.1764", "698", "0.4343", "699", "0.5765", "700", "0.6037" };

  private static String[] sBm25FDRawP10 = new String[] {
    "601", "0.3000", "602", "0.1000", "603", "0.4000", "604", "0.6000", "605", "0.3000",
    "606", "0.4000", "607", "0.4000", "608", "0.1000", "609", "0.6000", "610", "0.0000",
    "611", "0.3000", "612", "0.8000", "613", "0.2000", "614", "0.1000", "615", "0.0000",
    "616", "1.0000", "617", "0.6000", "618", "0.3000", "619", "0.7000", "620", "0.1000",
    "621", "0.8000", "622", "0.4000", "623", "0.5000", "624", "0.5000", "625", "0.1000",
    "626", "0.0000", "627", "0.1000", "628", "0.4000", "629", "0.2000", "630", "0.3000",
    "631", "0.4000", "632", "0.6000", "633", "1.0000", "634", "0.8000", "635", "0.8000",
    "636", "0.4000", "637", "0.7000", "638", "0.3000", "639", "0.2000", "640", "0.5000",
    "641", "0.5000", "642", "0.3000", "643", "0.4000", "644", "0.4000", "645", "0.9000",
    "646", "0.4000", "647", "0.6000", "648", "0.5000", "649", "1.0000", "650", "0.0000",
    "651", "0.3000", "652", "0.8000", "653", "0.6000", "654", "0.9000", "655", "0.0000",
    "656", "0.7000", "657", "0.5000", "658", "0.3000", "659", "0.3000", "660", "0.9000",
    "661", "0.9000", "662", "0.9000", "663", "0.6000", "664", "0.7000", "665", "0.3000",
    "666", "0.0000", "667", "0.8000", "668", "0.5000", "669", "0.2000", "670", "0.3000",
    "671", "0.5000", "672", "0.0000", "673", "0.5000", "674", "0.2000", "675", "0.4000",
    "676", "0.3000", "677", "0.7000", "678", "0.4000", "679", "0.6000", "680", "0.4000",
    "681", "0.5000", "682", "0.5000", "683", "0.4000", "684", "0.2000", "685", "0.4000",
    "686", "0.4000", "687", "0.8000", "688", "0.3000", "689", "0.0000", "690", "0.0000",
    "691", "0.4000", "692", "0.7000", "693", "0.5000", "694", "0.5000", "695", "0.7000",
    "696", "0.8000", "697", "0.3000", "698", "0.3000", "699", "0.7000", "700", "0.8000" };

  @Test
  public void runRegression() throws Exception {
    String[] params = new String[] {
            "data/trec/run.robust04.basic.xml",
            "data/trec/queries.robust04.xml" };

    FileSystem fs = FileSystem.getLocal(new Configuration());

    BatchQueryRunner qr = new BatchQueryRunner(params, fs);
    long start = System.currentTimeMillis();
    qr.runQueries();
    long end = System.currentTimeMillis();
    LOG.info("Total query time: " + (end - start) + "ms");

    verifyAllResults(qr.getModels(), qr.getAllResults(), qr.getDocnoMapping(),
        new Qrels("data/trec/qrels.robust04.noCRFR.txt"));
  }

  public static void verifyAllResults(Set<String> models,
      Map<String, Map<String, Accumulator[]>> results, DocnoMapping mapping, Qrels qrels) {
    Map<String, Map<String, Float>> AllModelsAPScores = Maps.newHashMap();
    AllModelsAPScores.put("robust04-dir-base", loadScoresIntoMap(sDirBaseRawAP));
    AllModelsAPScores.put("robust04-dir-sd", loadScoresIntoMap(sDirSDRawAP));
    AllModelsAPScores.put("robust04-dir-fd", loadScoresIntoMap(sDirFDRawAP));
    AllModelsAPScores.put("robust04-bm25-base", loadScoresIntoMap(sBm25BaseRawAP));
    AllModelsAPScores.put("robust04-bm25-sd", loadScoresIntoMap(sBm25SDRawAP));
    AllModelsAPScores.put("robust04-bm25-fd", loadScoresIntoMap(sBm25FDRawAP));

    Map<String, Map<String, Float>> AllModelsP10Scores = Maps.newHashMap();
    AllModelsP10Scores.put("robust04-dir-base", loadScoresIntoMap(sDirBaseRawP10));
    AllModelsP10Scores.put("robust04-dir-sd", loadScoresIntoMap(sDirSDRawP10));
    AllModelsP10Scores.put("robust04-dir-fd", loadScoresIntoMap(sDirFDRawP10));
    AllModelsP10Scores.put("robust04-bm25-base", loadScoresIntoMap(sBm25BaseRawP10));
    AllModelsP10Scores.put("robust04-bm25-sd", loadScoresIntoMap(sBm25SDRawP10));
    AllModelsP10Scores.put("robust04-bm25-fd", loadScoresIntoMap(sBm25FDRawP10));
    
    for (String model : models) {
      LOG.info("Verifying results of model \"" + model + "\"");
      verifyResults(model, results.get(model),
          AllModelsAPScores.get(model), AllModelsP10Scores.get(model), mapping, qrels);
      LOG.info("Done!");
    }
  }

  private static void verifyResults(String model, Map<String, Accumulator[]> results,
      Map<String, Float> apScores, Map<String, Float> p10Scores, DocnoMapping mapping,
      Qrels qrels) {
    float apSum = 0, p10Sum = 0;
    for (String qid : results.keySet()) {
      float ap = (float) RankedListEvaluator.computeAP(results.get(qid), mapping,
          qrels.getReldocsForQid(qid));

      float p10 = (float) RankedListEvaluator.computePN(10, results.get(qid), mapping,
          qrels.getReldocsForQid(qid));

      apSum += ap;
      p10Sum += p10;

      LOG.info("verifying qid " + qid + " for model " + model);
      assertEquals(apScores.get(qid), ap, 10e-6);
      assertEquals(p10Scores.get(qid), p10, 10e-6);
    }

    // One topic didn't contain qrels, so trec_eval only picked up 99 topics.
    float MAP = (float) RankedListEvaluator.roundTo4SigFigs(apSum / 99f);
    float P10Avg = (float) RankedListEvaluator.roundTo4SigFigs(p10Sum / 99f);

    if (model.equals("robust04-dir-base")) {
      assertEquals(0.3063, MAP, 10e-5);
      assertEquals(0.4424, P10Avg, 10e-5);
    } else if (model.equals("robust04-dir-sd")) {
      assertEquals(0.3194, MAP, 10e-5);
      assertEquals(0.4485, P10Avg, 10e-5);
    } else if (model.equals("robust04-dir-fd")) {
      assertEquals(0.3253, MAP, 10e-5);
      assertEquals(0.4576, P10Avg, 10e-5);
    } else if (model.equals("robust04-bm25-base")) {
      assertEquals(0.3033, MAP, 10e-5);
      assertEquals(0.4283, P10Avg, 10e-5);
    } else if (model.equals("robust04-bm25-sd")) {
      assertEquals(0.3212, MAP, 10e-5);
      assertEquals(0.4505, P10Avg, 10e-5);
    } else if (model.equals("robust04-bm25-fd")) {
      assertEquals(0.3213, MAP, 10e-5);
      assertEquals(0.4545, P10Avg, 10e-5);
    }
  }

  public static junit.framework.Test suite() {
    return new JUnit4TestAdapter(Robust04_Basic.class);
  }
}
