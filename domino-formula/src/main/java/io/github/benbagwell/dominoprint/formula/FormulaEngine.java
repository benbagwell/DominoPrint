package io.github.benbagwell.dominoprint.formula;

import io.github.benbagwell.dominoprint.model.DominoDateTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Offline interpreter. No Domino classes, HTTP, filesystem or scripting engines are used. */
public final class FormulaEngine {
    private final FunctionRegistry registry;
    public FormulaEngine() { registry=FunctionRegistry.standard(); }
    public FormulaEngine(FunctionRegistry registry) { this.registry=Objects.requireNonNull(registry); }
    public FunctionRegistry functions() { return registry; }
    public CompiledFormula compile(String source) { return new Parser(Objects.requireNonNull(source)).compile(); }
    public EvaluationResult evaluate(String source, EvaluationContext context) { return evaluate(compile(source),context); }
    public EvaluationResult evaluate(CompiledFormula formula, EvaluationContext context) {
        var state=new State(this,context); FormulaValue value;
        try { value=formula.root.eval(state); }
        catch(ReturnSignal r) { value=r.value; }
        catch(LimitSignal l) { value=FormulaValue.error("EVALUATION_LIMIT",l.getMessage()); }
        catch(IllegalArgumentException | ArithmeticException e) { value=FormulaValue.error("EVALUATION_ERROR",Objects.requireNonNullElse(e.getMessage(),"Invalid formula value")); }
        return new EvaluationResult(value,state.fields,state.steps);
    }
    interface Expr {
        FormulaValue run(State state);
        default FormulaValue eval(State state) { state.tick(); if(++state.depth>state.context.maxDepth()) { state.depth--; throw new LimitSignal("Expression nesting limit exceeded"); } try { return state.check(run(state)); } finally { state.depth--; } }
    }
    static final class State {
        final FormulaEngine engine; final EvaluationContext context; final Map<String,FormulaValue> fields, locals=new HashMap<>(); int steps,depth;
        State(FormulaEngine engine,EvaluationContext context) { this.engine=engine; this.context=context; fields=new LinkedHashMap<>(context.fields()); }
        void tick() { if(++steps>context.maxSteps()) throw new LimitSignal("Evaluation step limit exceeded"); }
        FormulaValue check(FormulaValue value) { if(value.size()>context.maxListElements()) throw new LimitSignal("List size limit exceeded"); return value; }
        FormulaValue get(String name) { name=key(name); return locals.containsKey(name)?locals.get(name):fields.getOrDefault(name,FormulaValue.unavailable()); }
        FormulaValue attribute(String name) { return context.attributes().getOrDefault(key(name),FormulaValue.error("MISSING_CONTEXT","Required context: " + name)); }
        void setField(String name,FormulaValue value) { if(value.type()==FormulaValue.Type.NOTHING) fields.remove(key(name)); else fields.put(key(name),value); }
    }
    private static final class ReturnSignal extends RuntimeException { final FormulaValue value; ReturnSignal(FormulaValue value) { super(null,null,false,false); this.value=value; } }
    static final class LimitSignal extends RuntimeException { LimitSignal(String message) { super(message,null,false,false); } }
    static String key(String value) { return value.toLowerCase(Locale.ROOT); }
    record Constant(FormulaValue value) implements Expr { public FormulaValue run(State s) { return value; } }
    record Variable(String name) implements Expr { public FormulaValue run(State s) { return s.get(name); } }
    record Sequence(List<Expr> expressions) implements Expr {
        public Sequence { expressions=List.copyOf(expressions); }
        public FormulaValue run(State s) { var value=FormulaValue.text(""); for(var expr:expressions) { value=expr.eval(s); if(value.isError()) return value; } return value; }
    }
    record Assignment(String name,String scope,Expr expression) implements Expr {
        public FormulaValue run(State s) {
            if(scope.equals("DEFAULT") && s.fields.containsKey(key(name))) return s.fields.get(key(name));
            var value=expression.eval(s); if(value.isError()) return value;
            if(scope.equals("FIELD") || scope.equals("DEFAULT")) s.setField(name,value); else s.locals.put(key(name),value); return value;
        }
    }
    record Unary(String operator,Expr expression) implements Expr {
        public FormulaValue run(State s) {
            var v=expression.eval(s); if(v.isError()) return v;
            if(operator.equals("!")) return FormulaValue.bool(!v.truth());
            if(v.type()!=FormulaValue.Type.NUMBER) return FormulaValue.error("TYPE_MISMATCH","Unary operator requires numbers");
            return FormulaValue.list(v.type(),v.elements().stream().map(x -> operator.equals("-")?-(Double)x:x).toList());
        }
    }
    record Binary(String operator,Expr left,Expr right) implements Expr {
        public FormulaValue run(State s) { var a=left.eval(s); if(a.isError()) return a; var b=right.eval(s); if(b.isError()) return b; return operations(operator,a,b,s); }
    }
    record Subscript(Expr list,Expr index) implements Expr {
        public FormulaValue run(State s) { var a=list.eval(s); var b=index.eval(s); if(a.isError())return a;if(b.isError())return b; int i=(int)b.number();return a.at(i<0?a.size()+i:i-1); }
    }
    record Call(String name,List<Expr> args) implements Expr {
        public Call { args=List.copyOf(args); }
        public FormulaValue run(State s) {
            try {
                switch(name) {
                    case "if": {
                        if(args.size()<3 || args.size()%2==0) return arity(name);
                        for(int i=0;i<args.size()-1;i+=2) { var test=args.get(i).eval(s);if(test.isError())return test;if(test.truth())return args.get(i+1).eval(s); }
                        return args.get(args.size()-1).eval(s);
                    }
                    case "iferror": { if(args.size()<1 || args.size()>2)return arity(name);var value=args.get(0).eval(s);if(!value.isError())return value;s.locals.put("$error",FormulaValue.text(value.error().message()));return args.size()==2?args.get(1).eval(s):FormulaValue.text(""); }
                    case "do": { return new Sequence(args).eval(s); }
                    case "return": { if(args.size()!=1)return arity(name);throw new ReturnSignal(args.get(0).eval(s)); }
                    case "isavailable", "isunavailable": { if(args.size()!=1)return arity(name);var v=args.get(0).eval(s);return FormulaValue.bool(name.equals("isavailable")?v.type()!=FormulaValue.Type.UNAVAILABLE:v.type()==FormulaValue.Type.UNAVAILABLE); }
                    case "iserror": { if(args.size()!=1)return arity(name);return FormulaValue.bool(args.get(0).eval(s).isError()); }
                    case "isnull": { if(args.size()!=1)return arity(name);var v=args.get(0).eval(s);return FormulaValue.bool(v.type()==FormulaValue.Type.TEXT&&v.size()==1&&v.string().isEmpty()); }
                    case "for": {
                        if(args.size()<4)return arity(name);var start=args.get(0).eval(s);if(start.isError())return start;var value=FormulaValue.text("");
                        while(true) { var test=args.get(1).eval(s);if(test.isError())return test;if(!test.truth())break;for(int i=3;i<args.size();i++){value=args.get(i).eval(s);if(value.isError())return value;}var step=args.get(2).eval(s);if(step.isError())return step;s.tick(); } return FormulaValue.number(1);
                    }
                    case "while", "dowhile": {
                        if(args.size()<2)return arity(name);var value=FormulaValue.text("");
                        if(name.equals("dowhile")){do{for(int i=0;i<args.size()-1;i++){value=args.get(i).eval(s);if(value.isError())return value;}var test=args.get(args.size()-1).eval(s);if(test.isError())return test;if(!test.truth())break;s.tick();}while(true);}
                        else while(true){var test=args.get(0).eval(s);if(test.isError())return test;if(!test.truth())break;for(int i=1;i<args.size();i++){value=args.get(i).eval(s);if(value.isError())return value;}s.tick();}
                        return FormulaValue.number(1);
                    }
                    case "transform": {
                        if(args.size()!=3)return arity(name);var list=args.get(0).eval(s);var variable=args.get(1).eval(s);if(list.isError())return list;if(variable.isError())return variable;
                        String key=key(variable.string());boolean existed=s.locals.containsKey(key);var prior=s.locals.get(key);var out=FormulaValue.nothing();
                        try { for(int i=0;i<list.size();i++){s.locals.put(key,list.at(i));out=s.check(out.append(args.get(2).eval(s)));if(out.isError())return out;}return out; }
                        finally { if(existed)s.locals.put(key,prior);else s.locals.remove(key); }
                    }
                    case "eval": { if(args.size()!=1)return arity(name);var source=args.get(0).eval(s);if(source.isError())return source;if(source.type()!=FormulaValue.Type.TEXT)return FormulaValue.error("TYPE_MISMATCH","@Eval requires text");var result=FormulaValue.text("");try{for(var text:source.elements()){result=s.engine.compile((String)text).root.eval(s);if(result.isError())return result;}return result;}catch(FormulaSyntaxException e){return FormulaValue.error("FORMULA_SYNTAX",e.getMessage());} }
                    case "set": { if(args.size()!=2)return arity(name);var target=args.get(0).eval(s);var value=args.get(1).eval(s);if(target.isError())return target;if(value.isError())return value;s.locals.put(key(target.string()),value);return value; }
                    case "setfield": { if(args.size()!=2)return arity(name);var target=args.get(0).eval(s);var value=args.get(1).eval(s);if(target.isError())return target;if(value.isError())return value;s.setField(target.string(),value);return value; }
                    case "getfield": { if(args.size()!=1)return arity(name);var target=args.get(0).eval(s);if(target.isError())return target;return s.fields.getOrDefault(key(target.string()),FormulaValue.unavailable()); }
                }
                List<FormulaValue> values=new ArrayList<>();for(var arg:args) { var value=arg.eval(s);if(value.isError())return value;values.add(value); }
                return s.engine.registry.invoke(name,values,s);
            } catch(IllegalArgumentException | ArithmeticException e) { return FormulaValue.error("FUNCTION_ERROR","@" + name + ": " + Objects.requireNonNullElse(e.getMessage(),"Invalid arguments")); }
        }
    }
    private static FormulaValue arity(String name) { return FormulaValue.error("ARGUMENT_COUNT","Invalid argument count for @" + name); }
    static FormulaValue operations(String op,FormulaValue a,FormulaValue b,State s) {
        if(op.equals(":"))return s.check(a.append(b));
        if(a.type()==FormulaValue.Type.UNAVAILABLE || b.type()==FormulaValue.Type.UNAVAILABLE)return FormulaValue.error("UNAVAILABLE_VALUE","Field is unavailable");
        if(op.equals("&") || op.equals("|"))return FormulaValue.bool(op.equals("&")?a.truth()&&b.truth():a.truth()||b.truth());
        boolean permuted=op.startsWith("*") && op.length()>1;
        String scalar=permuted?op.substring(1):op;
        boolean comparison=Set.of("=","!=","<>","<",">","<=",">=").contains(scalar);
        if(a.type()!=b.type() && !(a.type()==FormulaValue.Type.DATETIME && b.type()==FormulaValue.Type.NUMBER && Set.of("+","-").contains(scalar)))return FormulaValue.error("TYPE_MISMATCH","Operator operands have different types");
        if(a.size()==0 || b.size()==0)return FormulaValue.error("EMPTY_LIST","Operator requires a nonempty list");
        long count=permuted?(long)a.size()*b.size():Math.max(a.size(),b.size());if(count>s.context.maxListElements())throw new LimitSignal("List operation exceeds size limit");
        List<Object> out=new ArrayList<>();boolean matched=false;
        for(int i=0;i<count;i++) {
            s.tick();Object x=a.elements().get(permuted?i/b.size():Math.min(i,a.size()-1));Object y=b.elements().get(permuted?i%b.size():Math.min(i,b.size()-1));
            if(comparison) {
                int c=compare(x,y,s.context.zone());boolean result=switch(scalar){case "=" -> c==0;case "!=","<>" -> c!=0;case "<" -> c<0;case ">" -> c>0;case "<=" -> c<=0;default -> c>=0;};matched|=result;
            } else if(x instanceof String text && y instanceof String other && scalar.equals("+"))out.add(text+other);
            else if(x instanceof Double n && y instanceof Double m) {
                double r=switch(scalar){case "+" -> n+m;case "-" -> n-m;case "*" -> n*m;case "/" -> n/m;case "^" -> Math.pow(n,m);default -> throw new IllegalArgumentException("Invalid numeric operator: " + op);};
                if(!Double.isFinite(r))return FormulaValue.error("NUMERIC_ERROR","Invalid numeric result");out.add(r);
            } else if(x instanceof DominoDateTime dt && y instanceof Double seconds) {
                out.add(DominoDateTime.of(dt.toInstant(s.context.zone()).plusNanos((long)((scalar.equals("-")?-seconds:seconds)*1_000_000_000)).atZone(s.context.zone())));
            } else if(x instanceof DominoDateTime dx && y instanceof DominoDateTime dy && scalar.equals("-"))out.add((double)Duration.between(dy.toInstant(s.context.zone()),dx.toInstant(s.context.zone())).toMillis()/1000);
            else return FormulaValue.error("TYPE_MISMATCH","Operator does not apply to these values");
        }
        if(comparison)return FormulaValue.bool(matched);
        var type=out.get(0) instanceof Double?FormulaValue.Type.NUMBER:a.type();return FormulaValue.list(type,out);
    }
    static int compare(Object a,Object b,ZoneId zone) {
        if(a instanceof String x && b instanceof String y)return x.compareToIgnoreCase(y);
        if(a instanceof Double x && b instanceof Double y)return Double.compare(x,y);
        if(a instanceof DominoDateTime x && b instanceof DominoDateTime y) {
            if(x.date().isPresent() && y.date().isPresent() && (x.time().isEmpty() || y.time().isEmpty()))return x.date().get().compareTo(y.date().get());
            if(x.date().isEmpty() && y.date().isEmpty())return x.time().orElseThrow().compareTo(y.time().orElseThrow());
            return x.toInstant(zone).compareTo(y.toInstant(zone));
        }
        throw new IllegalArgumentException("Values cannot be compared");
    }
    private record Token(String kind,String text,int offset) {}
    private static final class Lexer {
        final String source;int pos;
        Lexer(String source){this.source=source;}
        Token next() {
            for(;;) {
            while(pos<source.length() && Character.isWhitespace(source.charAt(pos)))pos++;
            int start=pos;if(pos==source.length())return new Token("END","",pos);char c=source.charAt(pos++);
            if(c=='"' || c=='{') {
                char end=c=='{'?'}':'"';var out=new StringBuilder();boolean closed=false;
                while(pos<source.length()) { char ch=source.charAt(pos++);if(ch==end){closed=true;break;}if(ch=='\\'){if(pos>=source.length())throw error("Unterminated escape",start);char escaped=source.charAt(pos++);out.append(escaped=='n'?'\n':escaped=='r'?'\r':escaped=='t'?'\t':escaped);}else out.append(ch); }
                if(!closed)throw error("Unterminated string",start);return new Token("STRING",out.toString(),start);
            }
            if(Character.isDigit(c) || c=='.' && pos<source.length() && Character.isDigit(source.charAt(pos))) {
                while(pos<source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos)=='.'))pos++;
                if(pos<source.length() && (source.charAt(pos)=='e'||source.charAt(pos)=='E')){pos++;if(pos<source.length() && (source.charAt(pos)=='+'||source.charAt(pos)=='-'))pos++;while(pos<source.length() && Character.isDigit(source.charAt(pos)))pos++;}
                return new Token("NUMBER",source.substring(start,pos),start);
            }
            if(Character.isLetter(c)||c=='_'||c=='$'||c=='@') {
                while(pos<source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || "_$@.".indexOf(source.charAt(pos))>=0))pos++;
                String word=source.substring(start,pos);
                if(word.equalsIgnoreCase("REM") || word.equalsIgnoreCase("REMARK")) { while(pos<source.length() && source.charAt(pos)!=';')pos++;if(pos<source.length())pos++;continue; }
                return new Token("IDENT",word,start);
            }
            for(String op:List.of("*!=","*>=","*<=",":=","!=","<>","<=",">=","**","*/","*+","*-","*=","*<","*>"))if(source.startsWith(op,start)){pos=start+op.length();return new Token(op,op,start);}
            if("();:+-*/^=<>!&|[]".indexOf(c)>=0)return new Token(String.valueOf(c),String.valueOf(c),start);
            throw error("Unexpected character: " + c,start);
            }
        }
    }
    private static FormulaSyntaxException error(String message,int offset){return new FormulaSyntaxException(message,offset);}
    private static final class Parser {
        final String source;final Lexer lexer;Token token;int nesting;
        final Set<String> functions=new TreeSet<>(),fields=new TreeSet<>();
        Parser(String source){if(source.length()>1_000_000)throw error("Formula source exceeds size limit",0);this.source=source;lexer=new Lexer(source);token=lexer.next();}
        void next(){token=lexer.next();}
        boolean take(String kind){if(token.kind.equals(kind)){next();return true;}return false;}
        void expect(String kind){if(!take(kind))throw error("Expected " + kind,token.offset);}
        CompiledFormula compile(){var root=sequence("END");expect("END");return new CompiledFormula(source,root,functions,fields);}
        Expr sequence(String end){List<Expr> out=new ArrayList<>();while(!token.kind.equals(end) && !token.kind.equals("END")){out.add(expression(0));if(!take(";"))break;}return new Sequence(out);}
        Expr expression(int minimum) {
            if(++nesting>128)throw error("Formula nesting limit exceeded",token.offset);
            try {
                Expr left=prefix();
                while(true){int precedence=precedence(token.kind);if(precedence<minimum)break;String op=token.kind;next();if(op.equals(":=")){if(!(left instanceof Variable variable))throw error("Assignment requires a variable",token.offset);left=new Assignment(variable.name,"LOCAL",expression(precedence));}else left=new Binary(op,left,expression(precedence+(op.equals("^")?0:1)));}
                return left;
            }finally{nesting--;}
        }
        Expr prefix(){
            Token t=token;
            if(take("STRING"))return postfix(new Constant(FormulaValue.text(t.text)));
            if(take("NUMBER")){try{return postfix(new Constant(FormulaValue.number(Double.parseDouble(t.text))));}catch(NumberFormatException e){throw error("Invalid number",t.offset);}}
            if(take("(")){var e=sequence(")");expect(")");return postfix(e);}
            if(take("!") || take("-") || take("+"))return new Unary(t.kind,expression(8));
            if(take("[")) {
                int start=token.offset;while(!token.kind.equals("]") && !token.kind.equals("END"))next();int end=token.offset;expect("]");String value=source.substring(start,end).trim();
                if(value.matches("[A-Za-z][A-Za-z0-9_ ]*"))return new Constant(FormulaValue.keyword(value));
                try { var date=LocalDate.parse(value,DateTimeFormatter.ofPattern("M/d/uuuu"));return new Constant(FormulaValue.date(DominoDateTime.of(date))); }
                catch(java.time.format.DateTimeParseException e){try{return new Constant(FormulaValue.date(DominoDateTime.of(LocalDate.parse(value))));}catch(java.time.format.DateTimeParseException ignored){throw error("Unsupported date literal",start);}}
            }
            if(take("IDENT")) {
                String scope=t.text.toUpperCase(Locale.ROOT);
                if(Set.of("FIELD","DEFAULT").contains(scope)) {Token name=token;expect("IDENT");expect(":=");fields.add(key(name.text));return new Assignment(name.text,scope,expression(0));}
                if(scope.equals("ENVIRONMENT"))throw error("Environment writes are unavailable offline",t.offset);
                if(scope.equals("SELECT"))return expression(0);
                if(t.text.startsWith("@")){String name=key(t.text.substring(1));functions.add(name);List<Expr> args=new ArrayList<>();if(take("(")){if(!take(")")){do{args.add(expression(0));}while(take(";"));expect(")");}}return postfix(new Call(name,args));}
                fields.add(key(t.text));return postfix(new Variable(t.text));
            }
            throw error("Expected expression",token.offset);
        }
        Expr postfix(Expr expr){while(take("[")){var index=expression(0);expect("]");expr=new Subscript(expr,index);}return expr;}
        int precedence(String op){return switch(op){case ":="->0;case "|"->1;case "&"->2;case "=","!=","<>","<",">","<=",">=","*=","*!=","*<","*>","*<=","*>="->3;case "+","-","*+","*-"->4;case "*","/","**","*/"->5;case "^"->6;case ":"->9;default->-1;};}
    }
}
