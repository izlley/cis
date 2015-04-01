grammar cis_query;

cis_query
	: get_clause period_clause plot_clause_list option_clause? when_clause? ignore_clause?';'
	;

get_clause
	: 'GET' data_out_type
	;
	
data_out_type
	: 'png'|'ascii'|'json'|'html'
	;

period_clause
	: 'PERIOD' start_time ('TO' end_time)?
	;

start_time
	: date
	;

end_time
	: date
	;

plot_clause_list
	: plot_clause ('UNION' plot_clause)*
	;

plot_clause
	: 'PLOT' metric plotclause_opt? aggregator_clause? where_clause? legend_clause?
	;

plotclause_opt
    : 'ON' 'AXISY2' graphtype_opt?
    ;

graphtype_opt
    : 'WITH' 'GRAPHTYPE' plot_graphtypes
    ;

aggregator_clause
	: 'AGGREGATOR' aggregation_function (rate_option)? (downsample_option)?
	;

aggregation_function
	: 'sum'
	| 'min'
	| 'max'
	| 'avg'
	| 'dev'
	;

rate_option
	: 'OF' 'RATE'
	;

downsample_option
	: 'DOWNSAMPLING' downsampling_value
	;

downsampling_value
	: number('s'|'m'|'h'|'d'|'w'|'y') '-' aggregation_function
	;

legend_clause
    : 'LEGEND' '"' legend_string '"'
    ;

# Example of the legend_string : "<metric> <tagk>[1] <tagv>[1]" 

where_clause
	: 'WHERE' comparision_expr (('AND'|'OR') comparision_expr)*
	;

comparision_expr
	: 'VALUE' relop arithmatic_expr
	;

relop
	: '>'
	| '>='
	| '<'
	| '<='
	| '='
	| '!='
	;

arithmetic_expr
	: term arithmatic_expr`
	;

arithmatic_expr`
    : 
    | add_op term arithmatic_expr`
    ; 

add_op
    : '+' | '-'
    ;

term
    : factor term`
    ;

term`
    : 
    | mul_op factor term`
    ;

mul_op
    : '*' | '/'
    ;

factor
    : number
    | '(' arithmatic_expr ')'
    ;

when_clause
    : 'WHEN' event_phrase action_phrase
    ;
    
event_phrase
    : 'DATA' ('NON')? 'EXISTS'
    ;

action_phrase
    : 'NOTIFY' 'TO' mailaddr 'BY' 'EMAIL'
    ;

option_clause
	: 'OPTION' plot_option (plot_option)*
	;

plot_option
	: ('wxy=' dimensions)
	| ('yrange=' range)
	| ('y2range=' range)
	| ('zrange=' range)
	| ('ylabel=' string)
	| ('y2label=' string)
	| ('zlabel=' string) 
	| ('yformat=' format_str)
	| ('y2format=' format_str)
	| ('zformat=' format_str)
	| ('ylog=' number)
	| ('y2log=' number)
	| ('zlog=' number)
	| ('tagaxis=' tagaxis_option)
	| ('key=' key_option)
	| ('graphtype=' graphtypes)
	| ('fgcolor=' rgbcode)
	| ('bgcolor=' rgbcode)
	| ('nokey')
	| ('nocache')
	;

tagaxis_option
	: '(' ('x'|'y') ',' tagkey ')'
	;

graphtypes
	: 'line'
	| 'linepoint'
	| 'filledline'
	| 'impulse'
	| 'stack'
	| 'circle'
	| 'point'
	| 'box'
	;

plot_graphtypes
    : 'line'
    | 'linepoint'
    | 'filledline'
    | 'impulse'
    | 'point'
    | 'box'
    ;

rgbcode
    : x[0..1A..F][0..1A..F][0..1A..F][0..1A..F][0..1A..F][0..1A..F]
    ;
    
key_option
	: '(' (in'|'out') ',' ('left'|'center'|'right') '-' ('top'|'bottom'|'center') (',' 'horiz')? (',' 'box')? ')'
	;

date
	: relative_time
	| absolute_time
	| unix_time
	;
	
relative_time
	: number ('s'|'m'|'h'|'d'|'w'|'y') '-ago'
	;

absolute_time
	: yyyy '/' MM '/' dd '-' HH ':' mm ':' ss
	;

unix_time
	: number
	;

dimensions
	: number 'x' number
	;

range
	: (number|float) ':' (number|float)
	;
	
tag
	: 
	| '{' tagkey '=' tagvalue (',' tagkey '=' tagvalue)* '}'
	;
	
metric
	: id ('.' id)* tag?
	;
	
tagkey
	: id
	;
	
tagvalue
	: id ('|' id)*
	| '*'
	;

ignore_clause
	: 'IGNORE' number
	;

name
	: letter (letter|'0'..'9')*
	;

letter
	: 'A'..'Z'
	| 'a'..'z'
	| '_'
	;

number
	: ('0'..'9')+
	;
	
float
	: number '.' (number)*
	;

string
	:
	;
	
format_str
	:
	;

yyyy	:	
	;
MM	:	
	;
dd	:	
	;
HH	:	
	;
mm	:	
	;
ss	:	
	;
