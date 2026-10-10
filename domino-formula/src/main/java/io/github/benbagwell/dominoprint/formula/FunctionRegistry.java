package io.github.benbagwell.dominoprint.formula;

import io.github.benbagwell.dominoprint.model.*;
import java.math.*;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;

/** Immutable function catalog. Implemented does not mean Domino-conformance certified. */
public final class FunctionRegistry {
    public enum Status { IMPLEMENTED_UNVERIFIED, EXCLUDED, NOT_IMPLEMENTED }
    public record Specification(String name,int minimumArguments,int maximumArguments,Status status,String notes) {}
    @FunctionalInterface public interface OfflineFunction { FormulaValue apply(List<FormulaValue> arguments,EvaluationContext context); }
    @FunctionalInterface private interface Function { FormulaValue apply(List<FormulaValue> args,FormulaEngine.State state); }
    private record Entry(Specification specification,Function implementation) {}
    private final Map<String,Entry> entries;
    private FunctionRegistry(Map<String,Entry> entries){this.entries=Map.copyOf(entries);}
    public List<Specification> specifications(){return entries.values().stream().map(Entry::specification).sorted(Comparator.comparing(Specification::name)).toList();}
    public Optional<Specification> specification(String name){var e=entries.get(normalize(name));return e==null?Optional.empty():Optional.of(e.specification);}
    public boolean implemented(String name){return specification(name).map(s->s.status==Status.IMPLEMENTED_UNVERIFIED).orElse(false);}
    public FunctionRegistry withFunction(String name,int minimum,int maximum,OfflineFunction function){if(minimum<0||maximum<minimum)throw new IllegalArgumentException("Invalid arity");var copy=new LinkedHashMap<>(entries);add(copy,name,minimum,maximum,(a,s)->function.apply(List.copyOf(a),s.context),"Caller extension; must be thread-safe and perform no I/O");return new FunctionRegistry(copy);}
    FormulaValue invoke(String name,List<FormulaValue> args,FormulaEngine.State state){
        var entry=entries.get(normalize(name));
        if(entry==null)return FormulaValue.error("UNSUPPORTED_FUNCTION","Function not implemented: @" + name);
        if(entry.specification.status!=Status.IMPLEMENTED_UNVERIFIED)return FormulaValue.error("OFFLINE_UNAVAILABLE",entry.specification.notes);
        if(args.size()<entry.specification.minimumArguments||args.size()>entry.specification.maximumArguments)return FormulaValue.error("ARGUMENT_COUNT","Invalid argument count for @" + name);
        if(entry.implementation==null)return FormulaValue.error("INTERNAL_ERROR","Control function must be evaluated lazily");
        return state.check(entry.implementation.apply(args,state));
    }
    private static String normalize(String name){return name.replaceFirst("^@","").toLowerCase(Locale.ROOT);}
    private static void add(Map<String,Entry> map,String name,int min,int max,Function fn,String notes){String n=normalize(name);map.put(n,new Entry(new Specification(n,min,max,Status.IMPLEMENTED_UNVERIFIED,notes),fn));}
    private static void add(Map<String,Entry> map,String name,int min,int max,Function fn){add(map,name,min,max,fn,"Requires Domino differential verification before production certification");}
    public static FunctionRegistry standard(){
        Map<String,Entry> m=new LinkedHashMap<>();
        for(var n:List.of("If","IfError","Do","Return","For","While","DoWhile","Transform","Eval","Set","SetField","GetField","IsAvailable","IsUnavailable","IsError"))add(m,n,0,255,null,"Lazy control or local working-document function; requires conformance verification");
        add(m,"True",0,0,(a,s)->FormulaValue.number(1));add(m,"False",0,0,(a,s)->FormulaValue.number(0));add(m,"All",0,0,(a,s)->FormulaValue.number(1));
        add(m,"Nothing",0,0,(a,s)->FormulaValue.nothing());add(m,"DeleteField",0,0,(a,s)->FormulaValue.nothing());
        add(m,"Failure",1,1,(a,s)->FormulaValue.error("VALIDATION_FAILURE",a.get(0).string()));add(m,"Success",0,0,(a,s)->FormulaValue.number(1));
        add(m,"NewLine",0,0,(a,s)->FormulaValue.text("\n"));add(m,"Space",0,0,(a,s)->FormulaValue.text(" "));
        add(m,"Pi",0,0,(a,s)->FormulaValue.number(Math.PI));
        unary(m,"Abs",Math::abs);unary(m,"ACos",Math::acos);unary(m,"ASin",Math::asin);unary(m,"ATan",Math::atan);
        unary(m,"Cos",Math::cos);unary(m,"Sin",Math::sin);unary(m,"Tan",Math::tan);unary(m,"Exp",Math::exp);
        unary(m,"Ln",Math::log);unary(m,"Log",Math::log10);unary(m,"Sqrt",Math::sqrt);unary(m,"Sign",Math::signum);
        unary(m,"Integer",n->n<0?Math.ceil(n):Math.floor(n));
        binary(m,"ATan2",(x,y)->Math.atan2(y,x));binary(m,"Power",Math::pow);binary(m,"Modulo",(x,y)->x%y);
        add(m,"Round",1,2,(a,s)->numericPair(a.get(0),a.size()==2?a.get(1):FormulaValue.number(1),(x,f)->{if(f<=0)throw new IllegalArgumentException("Rounding factor must be positive");return Math.copySign(Math.floor(Math.abs(x/f)+0.5)*f,x);},s));
        add(m,"FloatEq",3,3,(a,s)->FormulaValue.bool(Math.abs(a.get(0).number()-a.get(1).number())<=Math.abs(a.get(2).number())));
        add(m,"Sum",1,255,(a,s)->FormulaValue.number(a.stream().flatMap(v->numbers(v).stream()).mapToDouble(Double::doubleValue).sum()));
        add(m,"Min",1,2,(a,s)->a.size()==1?FormulaValue.number(numbers(a.get(0)).stream().mapToDouble(Double::doubleValue).min().orElseThrow()):numericPair(a.get(0),a.get(1),Math::min,s));
        add(m,"Max",1,2,(a,s)->a.size()==1?FormulaValue.number(numbers(a.get(0)).stream().mapToDouble(Double::doubleValue).max().orElseThrow()):numericPair(a.get(0),a.get(1),Math::max,s));
        for(var n:List.of("Elements","Count"))add(m,n,1,1,(a,s)->FormulaValue.number(a.get(0).size()));
        add(m,"IsNumber",1,1,(a,s)->FormulaValue.bool(a.get(0).type()==FormulaValue.Type.NUMBER));
        add(m,"IsText",1,1,(a,s)->FormulaValue.bool(a.get(0).type()==FormulaValue.Type.TEXT));
        add(m,"IsTime",1,1,(a,s)->FormulaValue.bool(a.get(0).type()==FormulaValue.Type.DATETIME || a.get(0).type()==FormulaValue.Type.DATE_RANGE));
        add(m,"IsNull",1,255,(a,s)->FormulaValue.bool(a.stream().allMatch(v->v.size()==0 || v.type()==FormulaValue.Type.TEXT && v.elements().stream().allMatch(x->((String)x).isEmpty()))));
        textMap(m,"LowerCase",String::toLowerCase);textMap(m,"UpperCase",String::toUpperCase);
        add(m,"LowerCase",1,1,(a,s)->mapText(a.get(0),x->x.toLowerCase(s.context.locale())));
        add(m,"UpperCase",1,1,(a,s)->mapText(a.get(0),x->x.toUpperCase(s.context.locale())));
        textMap(m,"Trim",x->x.trim().replaceAll(" +"," "));
        add(m,"ProperCase",1,1,(a,s)->mapText(a.get(0),x->properCase(x,s.context.locale())));
        add(m,"Length",1,1,(a,s)->FormulaValue.list(FormulaValue.Type.NUMBER,texts(a.get(0)).stream().map(x->(double)x.length()).toList()));
        for(var n:List.of("Left","Right","LeftBack","RightBack"))add(m,n,2,2,(a,s)->substring(n,a.get(0),a.get(1),s));
        add(m,"Middle",3,3,(a,s)->middle(a,false,s));add(m,"MiddleBack",3,3,(a,s)->middle(a,true,s));
        add(m,"Contains",2,2,(a,s)->FormulaValue.bool(texts(a.get(0)).stream().anyMatch(x->texts(a.get(1)).stream().anyMatch(x::contains))));
        add(m,"Begins",2,2,(a,s)->FormulaValue.bool(texts(a.get(0)).stream().anyMatch(x->texts(a.get(1)).stream().anyMatch(x::startsWith))));
        add(m,"Ends",2,2,(a,s)->FormulaValue.bool(texts(a.get(0)).stream().anyMatch(x->texts(a.get(1)).stream().anyMatch(x::endsWith))));
        add(m,"Compare",2,3,(a,s)->{
            String mode=a.size()==3?a.get(2).string().toLowerCase(Locale.ROOT):"nocase";
            if(!Set.of("case","nocase").contains(mode))return FormulaValue.error("UNSUPPORTED_OPTION","@Compare option: " + mode);
            return textPair(a.get(0),a.get(1),(x,y)->FormulaValue.number(Integer.signum(mode.equals("case")?x.compareTo(y):x.compareToIgnoreCase(y))),s);
        });
        add(m,"ReplaceSubstring",3,3,(a,s)->{var from=texts(a.get(1));var to=texts(a.get(2));if(to.isEmpty())throw new IllegalArgumentException("Empty replacement list");return mapText(a.get(0),x->{for(int i=0;i<from.size();i++){s.tick();if(!from.get(i).isEmpty())x=x.replace(from.get(i),to.get(Math.min(i,to.size()-1)));}return x;});});
        add(m,"Replace",3,3,(a,s)->replace(a,s));
        add(m,"Explode",1,4,(a,s)->explode(a,s));
        add(m,"Implode",1,2,(a,s)->FormulaValue.text(String.join(a.size()==2?a.get(1).string():" ",texts(a.get(0)))));
        add(m,"Word",3,3,(a,s)->word(a,s));
        add(m,"Repeat",2,2,(a,s)->{
            int n=integer(a.get(1));if(n<0)throw new IllegalArgumentException("Negative repeat count");
            return mapText(a.get(0),x->{if((long)x.length()*n>1_000_000)throw new FormulaEngine.LimitSignal("Text size limit exceeded");return x.repeat(n);});
        });
        add(m,"Char",1,1,(a,s)->FormulaValue.list(FormulaValue.Type.TEXT,numbers(a.get(0)).stream().map(n->{int code=n.intValue();if(code<0||code>255)throw new IllegalArgumentException("Character out of range");return new String(new byte[]{(byte)code},Charset.forName("IBM850"));}).toList()));
        add(m,"Text",1,2,(a,s)->text(a,s));
        add(m,"TextToNumber",1,1,(a,s)->FormulaValue.list(FormulaValue.Type.NUMBER,texts(a.get(0)).stream().map(x->parseNumber(x,s.context.locale())).toList()));
        add(m,"ToNumber",1,1,(a,s)->a.get(0).type()==FormulaValue.Type.NUMBER?a.get(0):FormulaValue.list(FormulaValue.Type.NUMBER,texts(a.get(0)).stream().map(x->parseNumber(x,s.context.locale())).toList()));
        add(m,"TextToTime",1,1,(a,s)->parseDates(a.get(0),s));add(m,"ToTime",1,1,(a,s)->a.get(0).type()==FormulaValue.Type.DATETIME?a.get(0):parseDates(a.get(0),s));
        add(m,"Subset",2,2,(a,s)->{var v=a.get(0);int n=integer(a.get(1));if(n==0)throw new IllegalArgumentException("Subset count must not be zero");int count=(int)Math.min(Math.abs((long)n),v.size());return FormulaValue.list(v.type(),v.elements().subList(n<0?v.size()-count:0,n<0?v.size():count));});
        add(m,"Member",2,2,(a,s)->member(a.get(0),a.get(1),s));
        add(m,"IsMember",2,2,(a,s)->FormulaValue.bool(member(a.get(0),a.get(1),s).elements().stream().allMatch(x->(Double)x>0)));
        add(m,"IsNotMember",2,2,(a,s)->FormulaValue.bool(member(a.get(0),a.get(1),s).elements().stream().allMatch(x->(Double)x==0)));
        add(m,"Unique",1,1,(a,s)->{var v=a.get(0);List<Object> out=new ArrayList<>();for(Object x:v.elements()){s.tick();if(out.stream().noneMatch(y->equalAtom(x,y,s)))out.add(x);}return FormulaValue.list(v.type(),out);},"List form only; zero-argument identifier generation is unavailable");
        add(m,"Sort",1,2,(a,s)->{var v=a.get(0);String order=a.size()==2?a.get(1).string():"Ascending";if(!Set.of("ascending","descending").contains(order.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Unsupported sort options");var out=new ArrayList<>(v.elements());out.sort((x,y)->FormulaEngine.compare(x,y,s.context.zone()));if(order.equalsIgnoreCase("Descending"))Collections.reverse(out);return FormulaValue.list(v.type(),out);});
        add(m,"Select",2,255,(a,s)->{int i=integer(a.get(0));return i>0 && i<a.size()?a.get(i):FormulaValue.text("");});
        add(m,"Now",0,0,(a,s)->FormulaValue.date(DominoDateTime.of(s.context.now().atZone(s.context.zone()))));
        add(m,"Today",0,0,(a,s)->FormulaValue.date(DominoDateTime.of(s.context.now().atZone(s.context.zone()).toLocalDate())));
        add(m,"Yesterday",0,0,(a,s)->FormulaValue.date(DominoDateTime.of(s.context.now().atZone(s.context.zone()).toLocalDate().minusDays(1))));
        add(m,"Tomorrow",0,0,(a,s)->FormulaValue.date(DominoDateTime.of(s.context.now().atZone(s.context.zone()).toLocalDate().plusDays(1))));
        add(m,"Date",1,3,(a,s)->date(a,s));add(m,"Time",1,6,(a,s)->time(a,s));
        for(String n:List.of("Year","Month","Day","Weekday","Hour","Minute","Second"))add(m,n,1,1,(a,s)->dateComponent(n,a.get(0)));
        add(m,"Adjust",7,7,(a,s)->adjust(a,s));
        add(m,"BusinessDays",2,4,(a,s)->businessDays(a,s));
        add(m,"TimeMerge",2,3,(a,s)->timeMerge(a,s));
        add(m,"TimeZone",1,1,(a,s)->FormulaValue.list(FormulaValue.Type.NUMBER,dates(a.get(0)).stream().map(d->{if(d.offset().isEmpty())throw new IllegalArgumentException("Datetime has no zone");return -(double)d.offset().get().getTotalSeconds()/3600;}).toList()),"Returns offset hours west of UTC; Domino encoded fractional-zone conventions remain unverified");
        add(m,"Name",2,2,(a,s)->name(a));
        add(m,"URLDecode",2,2,(a,s)->mapText(a.get(1),x->URLDecoder.decode(x,StandardCharsets.UTF_8)),"UTF-8 URL decoding; Domino platform-specific modes remain unverified");
        add(m,"URLEncode",2,2,(a,s)->mapText(a.get(1),x->URLEncoder.encode(x,StandardCharsets.UTF_8)),"UTF-8 form encoding; Domino platform-specific modes remain unverified");
        textMap(m,"FileDir",x->{int i=Math.max(x.lastIndexOf('/'),x.lastIndexOf('\\'));return i<0?"":x.substring(0,i+1);});
        for(var n:List.of("UserName","UserNamesList","UserRoles","DbName","DbTitle","DbManager","DbServer","DbPath","ReplicaID","IsNewDoc","IsEditMode","IsResponseDoc","IsDocBeingLoaded","IsDocBeingRecalculated","IsDocBeingSaved","ClientType","Platform","Version","LanguagePreference","ThisName","ThisValue"))add(m,n,0,0,(a,s)->s.attribute(n));
        add(m,"DocumentUniqueID",0,0,(a,s)->s.attribute("unid"));add(m,"NoteID",0,0,(a,s)->s.attribute("noteid"));
        add(m,"Created",0,0,(a,s)->metadataDate("created",s));add(m,"Modified",0,0,(a,s)->metadataDate("modified",s));add(m,"Accessed",0,0,(a,s)->metadataDate("lastaccessed",s));
        add(m,"GetDocField",2,2,(a,s)->s.context.lookups().documentField(s.context.attributes().getOrDefault("dbpath",FormulaValue.text("")).display(""),a.get(0).string(),a.get(1).string()));
        add(m,"GetProfileField",2,3,(a,s)->s.context.lookups().profileField(s.context.attributes().getOrDefault("dbpath",FormulaValue.text("")).display(""),a.get(0).string(),a.size()==3?a.get(2).string():"",a.get(1).string()));
        add(m,"DbLookup",5,6,(a,s)->lookup(a,s));add(m,"DbColumn",4,4,(a,s)->column(a,s));
        add(m,"Translate",2,2,(a,s)->{var out=FormulaValue.nothing();for(String code:texts(a.get(1)))out=out.append(s.context.lookups().translate(a.get(0).string(),code));return out;},"Library extension: cached code translation, not an HCL formula function");
        for(var n:List.of("Command","PostedCommand","Prompt","PickList","DialogBox","SetDocField","SetProfileField","MailSend","DeleteDocument","DbCommand","Db2Schema","DDEExecute","DDEInitiate","DDEPoke","DDETerminate","Environment","GetHTTPHeader","SetHTTPHeader","GetPortsList","GetAddressBooks","DbDirectory","RefreshECL","UpdateFormulaContext","PostedCommand")) {
            String name=normalize(n);m.put(name,new Entry(new Specification(name,0,255,Status.EXCLUDED,"@"+n+" requires live resources, persistent side effects, or a UI and is unavailable in offline rendering"),null));
        }
        return new FunctionRegistry(m);
    }
    private static boolean equalAtom(Object a,Object b,FormulaEngine.State s){return a instanceof String? a.equals(b):FormulaEngine.compare(a,b,s.context.zone())==0;}
    private static List<String> texts(FormulaValue v){if(v.type()!=FormulaValue.Type.TEXT && v.type()!=FormulaValue.Type.KEYWORD)throw new IllegalArgumentException("Expected text list");return v.elements().stream().map(String.class::cast).toList();}
    private static List<Double> numbers(FormulaValue v){if(v.type()!=FormulaValue.Type.NUMBER)throw new IllegalArgumentException("Expected number list");return v.elements().stream().map(Double.class::cast).toList();}
    private static List<DominoDateTime> dates(FormulaValue v){if(v.type()!=FormulaValue.Type.DATETIME)throw new IllegalArgumentException("Expected datetime list");return v.elements().stream().map(DominoDateTime.class::cast).toList();}
    private static int integer(FormulaValue v){double d=v.number();if(d<Integer.MIN_VALUE||d>Integer.MAX_VALUE)throw new IllegalArgumentException("Integer out of range");return (int)d;}
    private static FormulaValue mapText(FormulaValue v,UnaryOperator<String> fn){return FormulaValue.list(FormulaValue.Type.TEXT,texts(v).stream().map(fn).toList());}
    private static void textMap(Map<String,Entry> m,String n,UnaryOperator<String> fn){add(m,n,1,1,(a,s)->mapText(a.get(0),fn));}
    private static void unary(Map<String,Entry> m,String n,DoubleUnaryOperator fn){add(m,n,1,1,(a,s)->numericMap(a.get(0),fn));}
    private static FormulaValue numericMap(FormulaValue v,DoubleUnaryOperator fn){List<Double> out=numbers(v).stream().map(fn::applyAsDouble).toList();if(out.stream().anyMatch(n->!Double.isFinite(n)))return FormulaValue.error("NUMERIC_ERROR","Numeric function result outside its domain");return FormulaValue.list(FormulaValue.Type.NUMBER,out);}
    private static void binary(Map<String,Entry> m,String n,DoubleBinaryOperator fn){add(m,n,2,2,(a,s)->numericPair(a.get(0),a.get(1),fn,s));}
    private static FormulaValue numericPair(FormulaValue a,FormulaValue b,DoubleBinaryOperator fn,FormulaEngine.State s){var x=numbers(a);var y=numbers(b);if(x.isEmpty()||y.isEmpty())throw new IllegalArgumentException("Empty number list");List<Double> out=new ArrayList<>();for(int i=0;i<Math.max(x.size(),y.size());i++){s.tick();double n=fn.applyAsDouble(x.get(Math.min(i,x.size()-1)),y.get(Math.min(i,y.size()-1)));if(!Double.isFinite(n))return FormulaValue.error("NUMERIC_ERROR","Numeric function result outside its domain");out.add(n);}return FormulaValue.list(FormulaValue.Type.NUMBER,out);}
    private static FormulaValue textPair(FormulaValue a,FormulaValue b,BiFunction<String,String,FormulaValue> fn,FormulaEngine.State s){var x=texts(a);var y=texts(b);if(x.isEmpty()||y.isEmpty())throw new IllegalArgumentException("Empty text list");var out=FormulaValue.nothing();for(int i=0;i<Math.max(x.size(),y.size());i++){s.tick();out=out.append(fn.apply(x.get(Math.min(i,x.size()-1)),y.get(Math.min(i,y.size()-1))));}return out;}
    private static String properCase(String value,Locale locale){var out=new StringBuilder();boolean first=true;for(int i=0;i<value.length();i++){char c=value.charAt(i);out.append(first?String.valueOf(c).toUpperCase(locale):String.valueOf(c).toLowerCase(locale));first=!Character.isLetterOrDigit(c);}return out.toString();}
    private static FormulaValue substring(String name,FormulaValue source,FormulaValue selector,FormulaEngine.State s){
        var out=FormulaValue.nothing();var sources=texts(source);int count=Math.max(sources.size(),selector.size());
        if(selector.type()!=FormulaValue.Type.NUMBER && selector.type()!=FormulaValue.Type.TEXT)throw new IllegalArgumentException("Expected number or text selector");
        for(int i=0;i<count;i++){s.tick();String x=sources.get(Math.min(i,sources.size()-1));Object y=selector.elements().get(Math.min(i,selector.size()-1));String value;
            if(y instanceof Double d){int n=(int)Math.min(Math.abs(d),x.length());if(d<0){value=x;}else value=switch(name){case "Left"->x.substring(0,n);case "Right"->x.substring(x.length()-n);case "LeftBack"->x.substring(0,x.length()-n);default->x.substring(n);};}
            else{String needle=(String)y;int p=name.endsWith("Back")?x.lastIndexOf(needle):x.indexOf(needle);value=p<0?"":name.startsWith("Left")?x.substring(0,p):x.substring(p+needle.length());}
            out=out.append(FormulaValue.text(value));
        }return out;
    }
    private static FormulaValue middle(List<FormulaValue> a,boolean backwards,FormulaEngine.State s){
        var selector=a.get(1);var end=a.get(2);return mapText(a.get(0),x->{s.tick();int start;
            if(selector.type()==FormulaValue.Type.NUMBER)start=backwards?x.length()-integer(selector):integer(selector);
            else{String needle=selector.string();int p=backwards?x.lastIndexOf(needle):x.indexOf(needle);if(p<0)return "";start=p+needle.length();}
            start=Math.max(0,Math.min(start,x.length()));int finish;
            if(end.type()==FormulaValue.Type.NUMBER)finish=Math.max(start,Math.min(x.length(),start+Math.max(0,integer(end))));
            else{finish=x.indexOf(end.string(),start);if(finish<0)return "";}return x.substring(start,finish);
        });
    }
    private static FormulaValue replace(List<FormulaValue> a,FormulaEngine.State s){var source=a.get(0);var from=a.get(1);var to=a.get(2);if(source.type()!=from.type()||source.type()!=to.type())throw new IllegalArgumentException("Replace types must match");if(to.size()==0)throw new IllegalArgumentException("Empty replacement list");List<Object> out=new ArrayList<>();for(var x:source.elements()){Object value=x;for(int i=0;i<from.size();i++){s.tick();if(FormulaEngine.compare(x,from.elements().get(i),s.context.zone())==0){value=to.elements().get(Math.min(i,to.size()-1));break;}}out.add(value);}return FormulaValue.list(source.type(),out);}
    private static FormulaValue explode(List<FormulaValue> a,FormulaEngine.State s){
        if(a.size()>2)return FormulaValue.error("UNSUPPORTED_OPTION","@Explode include-empty/newline options require conformance implementation");
        String separators=a.size()==2?a.get(1).string():" ,;";if(separators.isEmpty())return a.get(0);List<String> out=new ArrayList<>();
        for(var text:texts(a.get(0))){for(var part:text.split("["+Pattern.quote(separators)+"]+",-1)){s.tick();if(!part.isEmpty())out.add(part);}}return FormulaValue.list(FormulaValue.Type.TEXT,out);
    }
    private static FormulaValue word(List<FormulaValue> a,FormulaEngine.State s){String separator=a.get(1).string();int index=integer(a.get(2));if(separator.isEmpty())throw new IllegalArgumentException("Empty word separator");return mapText(a.get(0),x->{s.tick();String[] parts=x.split(Pattern.quote(separator),-1);int p=index<0?parts.length+index:index-1;return p<0||p>=parts.length?"":parts[p];});}
    private static FormulaValue member(FormulaValue needle,FormulaValue haystack,FormulaEngine.State s){if(needle.type()!=haystack.type())throw new IllegalArgumentException("Member types must match");List<Double> out=new ArrayList<>();for(var n:needle.elements()){double p=0;for(int i=0;i<haystack.size();i++){s.tick();if(equalAtom(n,haystack.elements().get(i),s)){p=i+1;break;}}out.add(p);}return FormulaValue.list(FormulaValue.Type.NUMBER,out);}
    private static FormulaValue text(List<FormulaValue> args,FormulaEngine.State s){var v=args.get(0);String format=args.size()==2?args.get(1).display(""):"";if(v.type()==FormulaValue.Type.TEXT)return v;List<String> out=new ArrayList<>();for(var x:v.elements()){s.tick();if(x instanceof Double d)out.add(formatNumber(d,format,s.context.locale()));else if(x instanceof DominoDateTime d)out.add(formatDate(d,format,s.context));else if(x instanceof DominoValue.DateRange r)out.add(formatDate(r.start(),format,s.context)+" - "+formatDate(r.end(),format,s.context));else out.add(x.toString());}return FormulaValue.list(FormulaValue.Type.TEXT,out);}
    public static String formatNumber(double value,String options,Locale locale){
        String f=options.toUpperCase(Locale.ROOT);if(!f.matches("[GFSC,()%0-9]*"))throw new IllegalArgumentException("Unsupported number format: " + options);
        var matcher=Pattern.compile("\\d+").matcher(f);int precision=matcher.find()?Integer.parseInt(matcher.group()):f.contains("C")||f.contains("S")?2:f.contains("F")?2:-1;
        if(precision>15)throw new IllegalArgumentException("Precision exceeds 15");
        if(options.isEmpty())return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        String pattern=f.contains("S")?"0"+(precision>0?"."+"0".repeat(precision):"")+"E00":(f.contains(",")?"#,##0":"0")+(precision>0?"."+"0".repeat(precision):"");
        if(f.contains("G") && precision<0)pattern=(f.contains(",")?"#,##0":"0")+".###############";
        if(f.contains("C"))pattern="¤"+pattern;if(f.contains("%"))pattern+="%";
        if(f.contains("()"))pattern+=";("+pattern+")";
        var formatter=new DecimalFormat(pattern,DecimalFormatSymbols.getInstance(locale));formatter.setRoundingMode(RoundingMode.HALF_UP);return formatter.format(value);
    }
    public static String formatDate(DominoDateTime date,String options,EvaluationContext context){
        String f=options.toUpperCase(Locale.ROOT);if(!f.matches("(?:D[0-3]|T[01]|Z[0-2]|S[0-3X])*"))throw new IllegalArgumentException("Unsupported date format: "+options);
        if(f.contains("S3"))throw new IllegalArgumentException("Relative S3 date formatting is not implemented");
        var d=date.date();var t=date.time();
        if(f.contains("Z0")&&d.isPresent()&&t.isPresent()){var z=date.toInstant(context.zone()).atZone(context.zone());d=Optional.of(z.toLocalDate());t=Optional.of(z.toLocalTime());}
        boolean showDate=!f.contains("S1")&&d.isPresent(),showTime=!f.contains("S0")&&t.isPresent();String result="";
        if(showDate){String pattern=f.contains("D3")?"MM/uuuu":f.contains("D2")||f.contains("D1")&&d.get().getYear()==context.now().atZone(context.zone()).getYear()?"MM/dd":"MM/dd/uuuu";result=d.get().format(DateTimeFormatter.ofPattern(pattern,context.locale()));}
        if(showTime){String pattern=f.contains("T0")?"hh:mm:ss a":"hh:mm a";result+=(result.isEmpty()?"":" ")+t.get().format(DateTimeFormatter.ofPattern(pattern,context.locale()));}
        if(f.contains("Z2")||f.contains("Z1")&&date.offset().isPresent()&&!date.offset().get().equals(context.zone().getRules().getOffset(context.now())))result+=" "+date.offset().map(Object::toString).orElse(context.zone().getId());return result;
    }
    private static double parseNumber(String value,Locale locale){if(value.isBlank())return 0;var p=new ParsePosition(0);Number n=NumberFormat.getNumberInstance(locale).parse(value.trim(),p);if(n==null||p.getIndex()!=value.trim().length())throw new IllegalArgumentException("Invalid numeric text: "+value);return n.doubleValue();}
    private static FormulaValue parseDates(FormulaValue v,FormulaEngine.State s){List<DominoDateTime> out=new ArrayList<>();for(String text:texts(v)){s.tick();out.add(parseDate(text,s.context));}return FormulaValue.list(FormulaValue.Type.DATETIME,out);}
    private static DominoDateTime parseDate(String value,EvaluationContext c){
        if(value.equalsIgnoreCase("Today"))return DominoDateTime.of(c.now().atZone(c.zone()).toLocalDate());
        if(value.equalsIgnoreCase("Yesterday"))return DominoDateTime.of(c.now().atZone(c.zone()).toLocalDate().minusDays(1));
        if(value.equalsIgnoreCase("Tomorrow"))return DominoDateTime.of(c.now().atZone(c.zone()).toLocalDate().plusDays(1));
        if(value.equalsIgnoreCase("Now"))return DominoDateTime.of(c.now().atZone(c.zone()));
        try{return DominoDateTime.of(ZonedDateTime.parse(value));}catch(DateTimeParseException ignored){}
        for(String pattern:List.of("M/d/uuuu h:mm a","M/d/uuuu H:mm:ss","uuuu-MM-dd'T'HH:mm:ss"))try{return DominoDateTime.of(LocalDateTime.parse(value,DateTimeFormatter.ofPattern(pattern,c.locale())).atZone(c.zone()));}catch(DateTimeParseException ignored){}
        for(String pattern:List.of("M/d/uuuu","uuuu-MM-dd"))try{return DominoDateTime.of(LocalDate.parse(value,DateTimeFormatter.ofPattern(pattern,c.locale())));}catch(DateTimeParseException ignored){}
        for(String pattern:List.of("h:mm a","H:mm:ss","H:mm"))try{return DominoDateTime.of(LocalTime.parse(value,DateTimeFormatter.ofPattern(pattern,c.locale())));}catch(DateTimeParseException ignored){}
        throw new IllegalArgumentException("Unsupported datetime text: "+value);
    }
    private static FormulaValue date(List<FormulaValue> a,FormulaEngine.State s){if(a.size()==1)return FormulaValue.list(FormulaValue.Type.DATETIME,dates(a.get(0)).stream().map(d->DominoDateTime.of(d.date().orElseThrow())).toList());if(a.size()!=3)throw new IllegalArgumentException("Date needs one or three arguments");return FormulaValue.date(DominoDateTime.of(LocalDate.of(integer(a.get(0)),1,1).plusMonths(integer(a.get(1))-1L).plusDays(integer(a.get(2))-1L)));}
    private static FormulaValue time(List<FormulaValue> a,FormulaEngine.State s){if(a.size()==1)return FormulaValue.list(FormulaValue.Type.DATETIME,dates(a.get(0)).stream().map(d->DominoDateTime.of(d.time().orElseThrow())).toList());if(a.size()==3)return FormulaValue.date(DominoDateTime.of(LocalTime.MIDNIGHT.plusHours(integer(a.get(0))).plusMinutes(integer(a.get(1))).plusSeconds(integer(a.get(2)))));if(a.size()==6)return FormulaValue.date(DominoDateTime.of(LocalDate.of(integer(a.get(0)),1,1).plusMonths(integer(a.get(1))-1L).plusDays(integer(a.get(2))-1L).atStartOfDay(s.context.zone()).plusHours(integer(a.get(3))).plusMinutes(integer(a.get(4))).plusSeconds(integer(a.get(5)))));throw new IllegalArgumentException("Time needs one, three, or six arguments");}
    private static FormulaValue dateComponent(String name,FormulaValue value){return FormulaValue.list(FormulaValue.Type.NUMBER,dates(value).stream().map(d->(double)switch(name){case "Year"->d.date().orElseThrow().getYear();case "Month"->d.date().orElseThrow().getMonthValue();case "Day"->d.date().orElseThrow().getDayOfMonth();case "Weekday"->d.date().orElseThrow().getDayOfWeek().getValue()%7+1;case "Hour"->d.time().orElseThrow().getHour();case "Minute"->d.time().orElseThrow().getMinute();default->d.time().orElseThrow().getSecond();}).toList());}
    private static FormulaValue adjust(List<FormulaValue> a,FormulaEngine.State s){List<DominoDateTime> out=new ArrayList<>();for(var value:dates(a.get(0))){s.tick();var d=value.date().map(x->x.plusYears(integer(a.get(1))).plusMonths(integer(a.get(2))).plusDays(integer(a.get(3))));var t=value.time();if(t.isPresent()){var combined=LocalDateTime.of(d.orElse(LocalDate.of(2000,1,1)),t.get()).plusHours(integer(a.get(4))).plusMinutes(integer(a.get(5))).plusSeconds(integer(a.get(6)));if(d.isPresent())d=Optional.of(combined.toLocalDate());t=Optional.of(combined.toLocalTime());}out.add(new DominoDateTime(d,t,value.offset(),value.daylightSaving(),""));}return FormulaValue.list(FormulaValue.Type.DATETIME,out);}
    private static FormulaValue businessDays(List<FormulaValue> a,FormulaEngine.State s){
        var starts=dates(a.get(0));var ends=dates(a.get(1));Set<Integer> excluded=new HashSet<>();if(a.size()>=3)numbers(a.get(2)).forEach(x->excluded.add(x.intValue()));else excluded.addAll(Set.of(1,7));Set<LocalDate> holidays=new HashSet<>();if(a.size()==4)dates(a.get(3)).forEach(d->holidays.add(d.date().orElseThrow()));List<Double> out=new ArrayList<>();
        for(int i=0;i<Math.max(starts.size(),ends.size());i++){var start=starts.get(Math.min(i,starts.size()-1)).date().orElseThrow();var end=ends.get(Math.min(i,ends.size()-1)).date().orElseThrow();double count=0;for(var d=start;!d.isAfter(end);d=d.plusDays(1)){s.tick();if(!excluded.contains(d.getDayOfWeek().getValue()%7+1)&&!holidays.contains(d))count++;}out.add(count);}return FormulaValue.list(FormulaValue.Type.NUMBER,out);
    }
    private static FormulaValue timeMerge(List<FormulaValue> a,FormulaEngine.State s){if(a.size()==3)return FormulaValue.error("UNSUPPORTED_OPTION","Explicit @TimeMerge time-zone argument not implemented");var ds=dates(a.get(0));var ts=dates(a.get(1));List<DominoDateTime> out=new ArrayList<>();for(int i=0;i<Math.max(ds.size(),ts.size());i++){var d=ds.get(Math.min(i,ds.size()-1));var t=ts.get(Math.min(i,ts.size()-1));out.add(new DominoDateTime(d.date(),t.time(),d.offset().isPresent()?d.offset():t.offset(),d.daylightSaving(),""));}return FormulaValue.list(FormulaValue.Type.DATETIME,out);}
    private static FormulaValue metadataDate(String key,FormulaEngine.State s){var v=s.attribute(key);if(v.isError()||v.type()==FormulaValue.Type.DATETIME)return v;try{return FormulaValue.date(DominoDateTime.parse(v.string(),false));}catch(IllegalArgumentException e){return FormulaValue.error("INVALID_CONTEXT","Invalid metadata date: "+key);}}
    private static FormulaValue name(List<FormulaValue> a){String mode=a.get(0).string().toLowerCase(Locale.ROOT);return mapText(a.get(1),value->{var parts=Arrays.asList(value.split("/"));return switch(mode){case "abbreviate"->String.join("/",parts.stream().map(x->x.replaceFirst("(?i)^(CN|OU|O|C)=","")).toList());case "cn"->parts.get(0).replaceFirst("(?i)^CN=","");case "canonicalize"->{if(value.regionMatches(true,0,"CN=",0,3))yield value;List<String> out=new ArrayList<>();for(int i=0;i<parts.size();i++)out.add((i==0?"CN=":i==parts.size()-1?"O=":"OU=")+parts.get(i));yield String.join("/",out);}case "o"->parts.stream().filter(x->x.regionMatches(true,0,"O=",0,2)).map(x->x.substring(2)).findFirst().orElse(parts.size()>1?parts.get(parts.size()-1):"");case "ou"->String.join("/",parts.stream().filter(x->x.regionMatches(true,0,"OU=",0,3)).map(x->x.substring(3)).toList());default->throw new IllegalArgumentException("Unsupported @Name keyword: "+mode);};});}
    private static String[] database(FormulaValue v){var values=texts(v);if(values.size()>2)throw new IllegalArgumentException("Invalid database location");return values.size()==1?new String[]{"",values.get(0)}:new String[]{values.get(0),values.get(1)};}
    private static void checkClass(FormulaValue value){var parts=texts(value);if(parts.size()>2)throw new IllegalArgumentException("Invalid lookup class/cache arguments");String type=parts.get(0);if(!type.isEmpty()&&!type.equalsIgnoreCase("Notes"))throw new IllegalArgumentException("Only cached Notes lookups supported");if(parts.size()==2&&!Set.of("","nocache","recache").contains(parts.get(1).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Unknown lookup cache option");}
    private static FormulaValue lookup(List<FormulaValue> a,FormulaEngine.State s){checkClass(a.get(0));String[] db=database(a.get(1));Set<String> keys=new HashSet<>();if(a.size()==6)texts(a.get(5)).forEach(x->keys.add(x.toLowerCase(Locale.ROOT)));if(!Set.of("failsilent","partialmatch","returndocumentuniqueid").containsAll(keys))return FormulaValue.error("UNSUPPORTED_OPTION","Unknown lookup keyword");return s.context.lookups().lookup(new LookupProvider.Request(db[0],db[1],a.get(2).string(),a.get(3),a.get(4),keys));}
    private static FormulaValue column(List<FormulaValue> a,FormulaEngine.State s){checkClass(a.get(0));String[] db=database(a.get(1));return s.context.lookups().column(db[0],db[1],a.get(2).string(),integer(a.get(3)));}
}
