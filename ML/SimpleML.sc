/*
Jonathan Reus (c) 2018 GPLv3

Simple Machine Learning Cuts and Preparations
*/


/*
A prediction algorithm based on a simple linear regression model.
The model assumes input and output data follows a linear relationship:
z = Mx + b

Where the values of M and b are optimized to best fit a set of training data.

This algorithm converts the search problem of finding the best M and b
to a minimization problem of minimizing a given cost function.

NTS: Minimization problems seem to be more efficiently solved than search...

See: https://towardsdatascience.com/introduction-to-machine-learning-algorithms-linear-regression-14c4e325882a

@usage



*/
SimpleLinearRegression {

	var <>m_term, <>b_term;
	var <>callback; // used to run a function between epochs if desired
	var <training; // training routine when temporal expansion delay is used

	*new {arg initm, initb;
		^super.new.init(initm, initb);
	}

	init {arg initm, initb;
		if(initm.isNil) { initm = rand(1.0) };
		if(initb.isNil) { initb = rand(1.0) };
		m_term = initm;
		b_term = initb;
	}

	/*
	trains the model given a training dataset of data (x) and labels (y)
	The training set should be normalized to 0.0 - 1.0
	predictions will also be made within this range
	@param x_train An array containing the x values of the training dataset
	@param y_train An array containing the y values of the training dataset
	@param epochs How many iterations of gradient descent should be used.
	@stepsize The learning rate, the damping of each step of gradient descent
	@delay a temporal delay between every 10 epochs in seconds, if not 0 then the training is done in a routine
	*/
	train {arg x_train, y_train, epochs=10, stepsize=0.1, temporal_expansion=0.0;
		var trainfunc;
		if(training.notNil) { training.stop };
		trainfunc = {
			var errors, meanSquareError, y_predict, numsamples;
			numsamples = x_train.size;

			epochs.do {arg epoch;
				y_predict = Array.newClear(numsamples);
				numsamples.do {arg i;
					y_predict[i] = this.predict(x_train[i]);
				};

				// This computes both the cost function (Mean Square Error)
				// and its partial derivatives with respect to m and b
				errors = (y_train - y_predict);
				meanSquareError = (errors**2).sum / numsamples;

				b_term = b_term + (stepsize * 2 * errors.sum / numsamples);
				m_term = m_term + (stepsize * 2 * (errors * x_train).sum / numsamples);

				if(epoch % 10 == 0) {
					"Epoch: %, MSE: %".format(epoch, meanSquareError).postln;
					if(callback.notNil) { callback.(this, meanSquareError) };
					if(temporal_expansion != 0) { temporal_expansion.wait };
				};

			}

		};
		training = nil;
		if(temporal_expansion != 0) { training = Task.new(trainfunc).play; } { trainfunc.(); };
		^training;
	}

	predict {arg x;
		var y;
		y = m_term * x + b_term;
		^y;
	}

	stop { if(training.notNil) { training.stop } }
	resume { if(training.notNil) { training.resume } }
}



/*
Basic N-dimensional 2-class linear classifier based on the Perceptron algorithm
The perceptron will converge only if the two training datasets are linearly separable.

activation(sum(x_i) + x_0) = 1|0

where sum goes from 1->N and x_0 is the bias

adapted from: https://machinelearningmastery.com/implement-perceptron-algorithm-scratch-python/
and: http://www.jeannicholashould.com/what-i-learned-implementing-a-classifier-from-scratch.html

*/
Perceptron {
	var <>activationFunc;
	var <>weights, <>bias;
	var <>callback;
	var <training;  // the training routine when training with temporal expansion

	*new {arg numinputs=2, afunc=\step;
		^super.new.init(numinputs, afunc);
	}

	init {arg numinputs, afunc;
		activationFunc = ActivationFunction.new(\step);
		weights = 1.0.dup(numinputs); // initialize weights to 1
		bias = 0.0; // initialize bias to 0
	}


	numInputs {
		^weights.size;
	}


	/*
	@return class prediction 1|0 given a sample vector x
	*/
	classify {arg sample;
		var sum;
		sum = (sample * weights).sum + bias;
		^activationFunc.(sum);
	}

	/*
	@param x1 dimension 1 [0 indexed]
	@param x2 dimension 2 [0 indexed]
	@return [slope,intercept] of the decision boundary line
	*/
	decisionBoundary {|x1=0,x2=1|
		var slope,intercept;
		slope = (weights[x1] / weights[x2]).neg;
		intercept = (bias / weights[x2]).neg;
		^[slope, intercept];
	}

	/*
	Use stochastic gradient descent to train a basic perceptron classifier
	DATA MUST BE NORMALIZED BEFORE CALLING THIS FUNCTION!
	@input training_datas  Training dataset matrix, examples are rows [[in1, in2, in3...],[...],..]
	@input train_labels   These are the output labels [1,0,0,0,1, ...]
	@input num_epochs  Number of epochs to train
	@input step_size  Step size/Learning rate, scaling factor step size in gradient decent whereby each weight is corrected each epoch
	@input temporal_expansion a delay time between callbacks
	@callback_interval run the callback every callback_interval epochs
	*/
	train {arg train_data, train_labels, num_epochs, step_size, temporal_expansion=0.01, callback_interval=10;
		var trainfunc;
		if(training.notNil) { training.stop };
		trainfunc = {
			var numsamples, errors, predictions, meanSquareError;
			if(train_data.size != train_labels.size) { "Training Data Mismatch".throw };
			numsamples = train_data.size;
			predictions = Array.newClear(numsamples);

			if(train_data[0].isArray.not) { // convert to 1d vectors if necessary
				train_data = train_data.collect {|item| [item] };
			};

			num_epochs.do {|epoch|
				numsamples.do {|i|
					predictions[i] = this.classify(train_data[i]);
				};
				errors = (train_labels - predictions); // NTS: this calculation of error is dependent on the two classes being 1 or 0!
				meanSquareError = (errors**2).sum / numsamples;

				train_data.do {|inputvec, i|
					inputvec.do {|input, j|
						weights[j] = weights[j] + (step_size * errors[i] * input);
					};
					bias = bias + (step_size * errors[i]);
				};

				//"EPOCH %".format(epoch).postln;

				if(epoch % 10 == 0) {
					"Epoch: %, MSE: %".format(epoch, meanSquareError).postln;
					if(callback.notNil) { callback.(this, meanSquareError, epoch) };
					if(temporal_expansion != 0) { temporal_expansion.wait };
				};
			};
		};
		training = nil;
		if(temporal_expansion != 0) { training = Task.new(trainfunc).play; } { trainfunc.(); };
		^training;
	}

	stop { if(training.notNil) { training.stop } }
	resume { if(training.notNil) { training.resume } }

}



// See: https://towardsdatascience.com/how-to-build-your-own-neural-network-from-scratch-in-python-68998a08e4f6
Cgraph {
	var <layers; // array of arrays, each with multiple Cnodes
	var <weights;

}

Cnode {
	var <>activationFunc;
}




/*

def train_weights(train, step_size, n_epoch):
weights = [0.0 for i in range(len(train[0]))]
for epoch in range(n_epoch):
sum_error = 0.0
for row in train:
prediction = predict(row, weights)
error = row[-1] - prediction
sum_error += error**2
weights[0] = weights[0] + step_size * error
for i in range(len(row)-1):
weights[i + 1] = weights[i + 1] + step_size * error * row[i]
print('>epoch=%d, lrate=%.3f, error=%.3f' % (epoch, step_size, sum_error))
return weights

*/

/*
Activation functions
*/
ActivationFunction {
	classvar <functions;

	*initClass {
		functions = Dictionary.new;

		functions[\step] = {|x| if(x > 0.0) { 1 } { 0 } };
		functions[\sigmoid] = {|x| 1 / (1 + (-1 * x).exp) };
		functions[\relu] = {|x| if(x > 0) {x} {0} };
	}

	*new {arg key;
		^functions[key];
	}
}

/*
Utility methods
*/
SimpleML {

}


/*****
COST FUNCTIONS

Cost Functions
We assume these cost functions are applied to a single neuron/perceptron with the input:

sum(x*w) + b = y

the input is fed into an activation function to produce an output.

σ(y) = a

Cost functions compare the training output with the predicted output to produce some kind of measure of error.

I'll use train to represent the desired output from the training set and pred for the predicted output from the current shape of modelling.


# Quadratic Cost: larger errors are made more promiment by the squaring.
# unfortunately, the calculation itself can slow down speed of learning.
def quadratic_cost(train, pred, n):
    return sum((train - pred)^2) / n


# Cross Entropy
# More efficient than quadratic cost function
# larger differences between train and pred will cause greater cost and encourage faster learning
def crossentropy_cost(train, pred, n):
    return (-1 / n) * sum((train * ln(pred)) + ((1-train) * ln(1-pred)))
​

*******/



