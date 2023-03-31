p = Perceptron.new(2);
p.weights;
p.activationFunc.(0.0);
p.activationFunc.(0.2);
p.predict([0.3, 0.2]);
p.plot; // not implemented yet

( // Some toy data...
d = [[[2.7810836,2.550537003],0],
	[[1.465489372,2.362125076],0],
	[[3.396561688,4.400293529],0],
	[[1.38807019,1.850220317],0],
	[[3.06407232,3.005305973],0],
	[[7.627531214,2.759262235],1],
	[[5.332441248,2.088626775],1],
	[[6.922596716,1.77106367],1],
	[[8.675418651,-0.242068655],1],
	[[7.673756466,3.508563011],1]]
);

p.train(d, 0.1 ,5);


Function

// Visualize a few activation functions
(
v = {|selector, start=(-5), stop=5|
	Array.interpolation(4000, start, stop)
	.collect({|x| x.perform(selector) })
	.plot.domainSpecs_([[start, stop, \lin, 0]]).specs_([[-1.5, 1.5, \lin, 0]])
	.setProperties(\gridOnX, true, \gridOnY, true);
};
);




// Activation functions are important as a choice in dissective practice...
v.(\step);
v.(\sigmoid);
v.(\tanh);
v.(\relu);
v.(\peak);
v.(\valley);


// Waveshaping
{ var sig = SinOsc.ar(10); [sig,sig.step,sig.sigmoid,sig.relu,sig.tanh] }.plot(0.3, minval: -1.2, maxval: 1.2);


// Waveshaping through a Perceptron? It would essentially be linear classification or prediction...
// This may be interesting if the "training data" is also dynamic in some way...



/*******************************
// Simple ScatterView Usage
********************************/
(
// Generate some toy data with a linear relationship
n = 100;
x = Array.series(n, 0.0, 100 / n) + Array.fill(n, {rrand(-10.0, 10.0)}).round(0.01);
y = Array.series(n, 1.0, 100 / n) + Array.fill(n, {rrand(-10.0, 10.0)}).round(0.01);
p = x.collect {|item,i| [item, y[i]] }; // as points

// Plot
b = Rect(0,0, 800, 600);
w = Window.new(bounds: b);
j = ScatterView.new(w, b, p, [0,100].asSpec, [0,100].asSpec);
j.symbolColor_(Color.blue).drawMethod_(\fillOval).symbolSize_(5@5);
w.front;
);

// Show axis, grid and value labels
j.drawAxes_(true).drawGrid_(true).drawValues_(true).refresh;

// Visual style
j.axisColor_(Color.gray(0.3)).gridColor_(Color.red).valuesColor_(Color.gray(0.7)).backgroundColor_(Color.black).refresh;


// Adjust axes & scale
j.setAxes([-50, 150, \lin].asSpec, [-50,150,\lin].asSpec).symbolSize_(3@3).drawValues_(false).refresh;
j.setAxes([1, 150, \exp].asSpec, [0, 100,\lin].asSpec).symbolSize_(5@5).drawGridValues_(true).refresh;




r = [1,100,\exp].asSpec;
r.minval;
r.maxval;
r.unmap(1);

j.drawGrid

j.gridColor_(Color.red).refresh
j.axisColor_(Color.gray(0.9)).refresh
j.gridColor
j.axisColor

Color.gray(0.1)
Color.black
Color.white


// TODO: Add grid functionality

// TODO: Layered plots / ScatterPlotterView to manage multiple dataset plots with different colors and to synch their axes
// TODO: Test/Add resizing ... make behavior a bit more normal, with a new window created when no parent is specified..
// TODO: Add help files


/*******************************
// END Simple ScatterView Usage
********************************/


/***************************************
Neural Network (multilayer)
****************************************/
// See: http://www.wildml.com/2015/09/implementing-a-neural-network-from-scratch/

(
n = ();
n.softmax = {|e,vec|
	var expvec = vec.exp;
	expvec / expvec.sum;
};
);

n.softmax([1,2,3,4]);

~graph = Cgraph.new(2, 1, \sigmoid); // size of input & output layer & output layer activation
~graph.addHiddenLayer(3, \sigmoid); // add 3-node hidden layer with given activation function




/***************************************
END Neural Network (multilayer)
****************************************/



/*
Quarks.install("https://github.com/ambisonictoolkit/PointView")
Quarks.install("https://github.com/ambisonictoolkit/SphericalDesign")
*/

a = Synthe.load
a.gui

p.weights[0]
p.weights[1]
p.weights[2]

// y crossing
x = (0 - p.weights[0]) / p.weights[1];

// x crossing
y = (0 - p.weights[0]) / p.weights[2];


w = PointView.new(bounds: 600@300);
w.points = d.collect {|e| Cartesian.new(e[0][0],e[0][1],0) };
w.points = w.points.add(Cartesian.new(x, 0, 0));
w.points = w.points.add(Cartesian.new(y, 0, 0));
w.connections = [[w.points.size-2,w.points.size-3]]; // make a connection between those last two
w.front;







/* Plotting */
w = Window.new("Plots", 400@200);
p = Plotter.new("Lineplot", 400@200, w).plotMode_(\linear);
l = Plotter.new("Dotplot", 400@200, w).plotMode_(\points);
p.value = (0..100).normalize.collect({|x| x*x; });
l.value = Array.fill(10, { [rand(100),rand(100)] });

l.value = (0..10).normalize;

l.setProperties(\plotColor, Color.blue, \backgroundColor, Color.clear);
w.refresh;


l.specs;
l.domainSpecs;



w.front;

l.data;
l.value = [0.2, 0.5];
l.specs = ControlSpec.new(0.0, 1.0, 'linear', 0.0, 0.0, "");
l.domainSpecs = ControlSpec.new(0.0, 100.0, 'linear', 0.0, 0.0);
l.specs;
l.domainSpecs;


Quarks.gui

(
w = Window.new("Scatter", 600@300);
a = ScatterView(w, 600@300, {|i| [sin(i/50), sin(0.05*i)]}!100, [-1,1].asSpec, [-1,1].asSpec);
w.front;
);



(
var numItems = 630;
w = Window.new(bounds: Rect(40, 40, 800, 150)).front;
a = ScatterView(w, Rect(10, 10, 760, 100), {|i| [sin(i/50), sin(0.05*i)]}!numItems, [-1,1].asSpec, [-1,1].asSpec);
b = ScatterView(w, Rect(10, 10, 760, 100), {|i| [sin(i/21), sin(0.05*i)]}!numItems, [-1,1].asSpec, [-1,1].asSpec);
a.background = Color.gray(0, 0);
a.isHighlight = true;
Slider(w, Rect(10, 120, 760, 10)).action_{|me|
	a.highlightItemRel = me.value;
	w.refresh;
	//a.highlightItem = (me.value*numItems).asInteger.min(numItems-1);
};

a.drawMethod = \lineTo;
a.symbolColor = Color.blue(0.7, 0.3);
a.highlightSize = 10@10;
w.refresh;

)
