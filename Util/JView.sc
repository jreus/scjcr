/*****************************************
(C) 2018 Jonathan Reus

Various simple and useful view widgets.

******************************************/

/***
@usage

// Using RadioButtons
(
w = Window.new("R", 200@100);
r = RadioButton.new(w, Rect(100,0,100,100));
t = StaticText(w, Rect(0,0,100,100)).string_("Option").background_(Color.gray(0.1)).stringColor_(Color.gray(0.9));
r.action_({|rad, sel| [rad,sel].postln }).background_(Color.gray(0.1)).traceColor_(Color.gray(0.9));
t.mouseUpAction = {|txt| r.setSelected(r.selected.not); txt.postln };
w.alwaysOnTop_(true);
w.front;
);


// Using RadioSetView
(
w = Window.new("R", 100@100);
r = RadioSetView.new(w, w.bounds);
r.add("Opt1"); r.add("Opt2"); r.add("Opt3");
r.action = {|sv, idx| [sv,idx].postln };
w.alwaysOnTop_(true);
w.front;
);

***/
RadioSetView : CompositeView {
  var <buttons, <traceColor, <font, <textAlign;
  var <radioWidth, <textWidth;
  var <selectedIndex=nil;
  var <>action;

  *new {|parent, bounds|
    ^super.new(parent,bounds).init;
  }

  init {
    this.decorator = FlowLayout(this.bounds, 0@0, 0@0);
    buttons = List.new;
    traceColor = Color.gray(0.1);
    this.background = Color.clear;
    font = Font("Arial", 10);
    textAlign = \left;
    radioWidth = 30;
    textWidth = 50;
    ^this;
  }
  font_ {|newfont| font=newfont; buttons.do{|btn| btn[0].font_(newfont) } }
  textAlign_ {|align| textAlign=align; buttons.do{|btn| btn[0].align_(align) } }
  radioWidth_ {|width| radioWidth=width; buttons.do{|btn| btn[1].resizeTo_(width,width) } }
  textWidth_ {|width| textWidth=width; buttons.do{|btn| btn[0].resizeTo_(width, this.radioWidth) } }

  traceColor_ {|newcolor|
    traceColor=newcolor;
    buttons.do{|btn| btn[0].stringColor_(newcolor); btn[1].traceColor_(newcolor) };
  }

  setSelected {|newindex|
    this.selectedIndex_(newindex);
    buttons[newindex][1].setSelected(true);
  }

  selectedIndex_{|newindex|
    if(newindex >= buttons.size) { "Index % exceeds number of radio buttons".format(newindex).error; ^nil };
    selectedIndex = newindex;
  }

  add {|text|
    var newbut, newtext;
    // add layout here...
    newtext = StaticText(this, textWidth@(this.bounds.height)).string_(text)
    .stringColor_(this.traceColor).font_(font).align_(textAlign);
    newbut = RadioButton(this, radioWidth@radioWidth)
    .background_(Color.clear).traceColor_(traceColor);
    newtext.mouseUpAction = {|txt| newbut.setSelected(newbut.selected.not) };
    newbut.action = {|vw, sel|
      this.buttons.do {|but, idx|
        var txt=but[0], btn=but[1];
        btn.value_(false);
        if(vw == btn) { this.selectedIndex_(idx); if(this.action.notNil) {this.action.(this, idx)} };
      };
      vw.value_(true);
    };
    buttons.add([newtext, newbut]);
  }


}

RadioButton : UserView {
  var <>traceColor, <>inset=0.2;
  var <>selected=false;
  var <>action;
  var <>display = \radio;

  // display types
  // \radio \x \check

  *new {|parent, bounds, type=\radio|
    ^super.new(parent, bounds).init(type);
  }

  init {|type|
    this.display = type;
    this.background = Color.grey(0.1);
    this.traceColor = Color.gray(0.9);
    this.drawFunc_({|vw|
      var width, height, inw, inh;
      width = vw.bounds.width;
      height = vw.bounds.height;
      inw = inset*width; inh = inset*height;
      Pen.use {
        Pen.color = this.background;
        Pen.addRect(vw.bounds);
        Pen.fill;
        switch(display,
          \radio, {
            Pen.color = this.traceColor;
            Pen.addOval(Rect(0,0,width,height));
            Pen.stroke;

            if(selected) {
              Pen.addOval(Rect(inw,inh,width-(2*inw),height-(2*inh)));
              Pen.fill;
            };

          },

          \x, {
            Pen.color = this.traceColor;
            Pen.addOval(Rect(0,0,width,height));
            Pen.stroke;

            if(selected) {
              Pen.width_(width/20 + 1);
              Pen.line((width/5)@(height/5), (width/1.2)@(height/1.25));
              Pen.line((width/5)@(height/1.2), (width/1.2)@(height/5));
              Pen.stroke;
            };

          },
          \check, {
            inw = inw * 0.4; inh = inh * 0.4;
            Pen.color = this.traceColor;
            Pen.addRect(Rect(inw,inh,width-(2*inw),height-(2*inh)));
            Pen.stroke;
            if(selected) {
              Pen.width_(width/20 + 1);
							Pen.line((inw*2)@(height/4), (width/2)@(height-(2*inh)));
							Pen.line((width/2)@(height-(2*inh)), (width-(2*inw))@(height/4));
              Pen.stroke;
            };

          },
		  \box, {
            inw = inw * 0.4; inh = inh * 0.4;
            Pen.color = this.traceColor;
            Pen.addRect(Rect(inw,inh,width-(2*inw),height-(2*inh)));
            Pen.stroke;

            if(selected) {
			Pen.addRect(Rect(inw*3, inh*3, width-(6*inw), height-(6*inh)));
            Pen.fill;
            };

          },
        );

      };
    });

    this.mouseUpAction_({|vw, xpos, ypos| this.setSelected(selected.not) });

    this.refresh;
  }

  value { ^selected }
  value_ {|val|
    selected = val;
    this.refresh;
  }

  setSelected {|val|
    selected = val;
    if(this.action.notNil) { this.action.(this, val) };
    this.refresh;
  }


}

