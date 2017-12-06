// Make line
makeLine(50, 200, 350, 200);

/* Adapted from http://imagej.1557.x6.nabble.com/plot-profiles-for-two-colors-td5002587.html */
b=1; //channel number for the blue channel 
y=2; //channel number for the yellow channel 
intensityBottom=0; intensityTop=255; //Intensity Range for the plot 
Stack.setChannel(b) // blue channel 
profile=getProfile(); 
Plot.create("2 Channels Line Plot", "Distance(Pixels)", "Intensity",profile); 
Plot.setLimits(0, profile.length-1, intensityBottom, intensityTop); //set plot range 
Stack.setChannel(y) // yellow channel 
profile=getProfile(); 
Plot.setColor("yellow"); //color for Plot.add 
Plot.add("line",profile); 
Plot.setColor("blue"); //color for first plot (Plot.create)
run("Add Selection...");
run("Select None");