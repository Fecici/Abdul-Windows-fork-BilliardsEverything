package patternfinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.InvalidCodeSequence;
import billiards.viewer.Progress;
// want to have just one utils file, but some utils are useful only for this pattern finder
// thus we have the file PatUtils as well.
import billiards.viewer.Utils;
import billiards.wrapper.ConnectionPool;
import billiards.codeseq.CodeType;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javaslang.control.Either;

public class PatternFinder {

	// GLOBAL MUTABLE STATE
	// we keep a backup incase they press clean and then super check, in order to be able to check
	// the correct results. THE ONLY PLACE THIS IS MODIFIED IS AT THE END OF THE fireCalcBtn ACTION.
	private String resultBackup = "";

    // the time the tool tips take to open and close, in seconds
    private static final double TipOpenDelay = 2;
    private static final double TipCloseDelay = 20;

	private static final Color clickColor = Color.GOLD;

    final Stage mainWindow;
    final ConnectionPool pool;

    final VBox mainVBox = new VBox();
    final Scene scene = new Scene(mainVBox, 1300, 732);
    final Button calcBtn = new Button();
    final CheckBox verifyCB = new CheckBox();
    final Button cleanBtn = new Button();
    final Button superCheckBtn = new Button();
    final Button upBtn = new Button();
    final Button downBtn = new Button();
    final Button calcAndXtBtn = new Button();
    final Button searchBtn = new Button();
    final Button oneCodeBtn = new Button();

    final CheckBox checkLRsCB = new CheckBox();
    final TextField checkLenField = new TextField();

    final Button removeDupesBtn = new Button();

    final Label printLbl = new Label();
    final ComboBox<String> outputCB = new ComboBox<>();

    final Label patSizeLbl = new Label();
    final TextField patSize = new TextField();

    final Button extendBtn = new Button();
    final TextField extMin = new TextField();
    final TextField extMax = new TextField();
    final Label extLable = new Label();

    final Label splitLbl = new Label();

    final TextArea codesField = new TextArea();
    final TextArea resultField = new TextArea();

    final CheckBox CScb = new CheckBox();
    final CheckBox OSOcb = new CheckBox();
    final CheckBox OSNOcb = new CheckBox();
    final CheckBox ONScb = new CheckBox();
    final CheckBox CNScb = new CheckBox();

    public PatternFinder(final Stage primaryStage, final String version,
                         final ConnectionPool pool, final String dbName) {

	mainWindow = primaryStage;
	this.pool = pool;

        final String windowTitle = String.format("Pattern Finder %s (%s)", version, dbName);

        Utils.setupCustomTooltipBehavior(
		(int) (TipOpenDelay * 1000), (int) (TipCloseDelay * 1000), 200);

	cleanBtn.setText("Clean");
	cleanBtn.setTooltip(Utils.toolTip("Clean the output field so the results may be put in "
			+ "the cover window."));
	Utils.colorButton(cleanBtn, Color.LIGHTGRAY, clickColor);
	cleanBtn.setOnAction(event -> fireCleanBtn());

	superCheckBtn.setText("Super Check");
	superCheckBtn.setTooltip(Utils.toolTip("Do the superCheck on the results Field."));
	Utils.colorButton(superCheckBtn, Color.THISTLE, clickColor);
	superCheckBtn.setOnAction(event -> fireSuperCheckBtn(true));

	extendBtn.setText("Add +");
	extendBtn.setTooltip(Utils.toolTip("Add '+' to all iteration lines in the input."));
	Utils.colorButton(extendBtn, Color.THISTLE, clickColor);
	extendBtn.setOnAction(event -> fireExtendBtn());

	calcBtn.setText("Calculate");
	calcBtn.setTooltip(Utils.toolTip("Calculate Action. Will search input Field for "
			+ "patterns, and will calculate any extensions."));
	Utils.colorButton(calcBtn, Color.THISTLE, clickColor);
	calcBtn.setOnAction(event -> fireCalcBtn());

	upBtn.setText(" ^ ");
	upBtn.setTooltip(Utils.toolTip("Put the text from the results field into the input "
			+ "field."));
	upBtn.setMaxHeight(20);
	Utils.colorButton(upBtn, Color.THISTLE, clickColor);
	upBtn.setOnAction(event -> {
		codesField.setText(resultField.getText());
		resultField.clear();
	});

	downBtn.setText(" v ");
	Utils.colorButton(downBtn, Color.THISTLE, clickColor);
	downBtn.setOnAction(event -> fireDownBtn());

        searchBtn.setText("Search");
        searchBtn.setTooltip(Utils.toolTip("Brings up a window that allows you to search for something"));
        Utils.colorButton(searchBtn, Color.LIGHTPINK, clickColor);
        searchBtn.setOnAction(event -> new SearchWindow(windowTitle, pool).show());

        oneCodeBtn.setText("1 Code");
        oneCodeBtn.setTooltip(Utils.toolTip("Use this to search for patterns using only one code."));
        Utils.colorButton(oneCodeBtn, Color.LIGHTPINK, clickColor);
        oneCodeBtn.setOnAction(event -> new OneCodeWindow(windowTitle).show());

	calcAndXtBtn.setText("Calc & Ext");
	calcAndXtBtn.setTooltip(Utils.toolTip("Preform a specific sequence of events."
			+ " See instructions for how to use this."));
	Utils.colorButton(calcAndXtBtn, Color.AQUAMARINE, clickColor);
	calcAndXtBtn.setOnAction(event -> {
	outputCB.setValue("Iteration: End");
	final String backUp = codesField.getText();

		// Going to str8 up just fire the buttons that do the sequence of commands we want
	fireCalcBtn();
	upBtn.fire();
	fireCalcBtn();
	fireSuperCheckBtn(false);
	codesField.setText(backUp);

	});

	removeDupesBtn.setText("DUPLICATES");
	removeDupesBtn.setOnAction(event -> {
		final FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Import Text");
            FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT files (*.txt)", "*.txt");
            fileChooser.getExtensionFilters().add(extFilter);
            final File file = fileChooser.showOpenDialog(mainWindow);

            if (file != null) {
	final Stream<String> stream;
	try {
					 stream = Files.lines(file.toPath());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

	MutableSet<String> results = new UnifiedSet<>();
	final StringBuilder input = new StringBuilder();

	for (String line : (Iterable<String>) stream::iterator) {
		results.add(Utils.trimCodeLine(line));
		input.append(line).append("\n");
	}
	stream.close();

	final StringBuilder builder = new StringBuilder();
	for (String line : results) {
		builder.append(line);
		builder.append("\n");
	}
	resultField.setText(builder.toString());
	codesField.setText(input.toString());
            }
	});

	CScb.setText("CS");
	CScb.setSelected(true);
	OSOcb.setText("OSO");
	OSOcb.setSelected(true);
	ONScb.setText("ONS");
	ONScb.setSelected(true);
	CNScb.setText("CNS");
	CNScb.setSelected(true);
	OSNOcb.setText("OSNO");
	OSNOcb.setSelected(true);

	checkLRsCB.setText("LR");
	checkLRsCB.setSelected(true);
	checkLenField.setText("500");
	checkLenField.setPrefWidth(80);
	checkLenField.setTooltip(Utils.toolTip("How far should we super check?"));

	verifyCB.setText("Check for Empties");
	verifyCB.setTooltip(Utils.toolTip("When calculating, if this is checked, the results "
			+ "will be tested to see if they are empty."));

	codesField.setPromptText("Enter Code Sequences");
	resultField.setEditable(false);
	VBox.setVgrow(codesField, Priority.SOMETIMES);
	VBox.setVgrow(resultField, Priority.SOMETIMES);

	outputCB.getItems().addAll("List All", "Iteration: Base", "Iteration: Start", "Iteration: End");
	outputCB.setTooltip(Utils.toolTip("In what format to output any found patterns."));
	outputCB.setPrefWidth(145);
	outputCB.setStyle(Utils.hex(Color.THISTLE));
	outputCB.setValue("List All");

	printLbl.setText("Printing:");

	extMin.setPrefWidth(50);
        extMin.setTooltip(Utils.toolTip("The min number of iterations you want to extend any "
                + "extension lines by."));
        extMin.setText("0");

	extMax.setPrefWidth(50);
	extMax.setTooltip(Utils.toolTip("The max number of iterations you want to extend any "
			+ "extension lines by."));
	extMax.setText("4");
	extLable.setText("Extend:");

	splitLbl.setText(" | ");

	patSize.setText("4");
	patSize.setTooltip(Utils.toolTip("The minimum pattern length you want to qualify as a "
			+ "useful pattern."));
	patSize.setPrefWidth(60);
	patSizeLbl.setText("Min. Length:");

	final HBox typesHBox = new HBox(10);
	typesHBox.setAlignment(Pos.CENTER);
	typesHBox.getChildren().addAll(CScb, OSOcb, CNScb, ONScb, OSNOcb);

	final HBox extMinMax = new HBox();
	extMinMax.setAlignment(Pos.CENTER);
	extMinMax.getChildren().addAll(extMin, extMax);

	final HBox controls = new HBox(10);
	controls.setPadding(new Insets(0));
	controls.setAlignment(Pos.CENTER);
	controls.getChildren().addAll(calcBtn, patSizeLbl, patSize, extLable, extMinMax,
	        printLbl, outputCB, splitLbl, searchBtn, extendBtn, superCheckBtn, checkLenField,
	        checkLRsCB, cleanBtn, upBtn,downBtn, oneCodeBtn);

	mainVBox.setSpacing(10);
	mainVBox.setPadding(new Insets(10, 10, 10, 10));
	mainVBox.getChildren().setAll(controls, typesHBox, codesField, resultField);

	mainWindow.setTitle(windowTitle);
        mainWindow.setOnCloseRequest(event -> {
            // close all the windows
            Platform.exit();
        });
        mainWindow.setScene(scene);
    }

    public void start() {
        mainWindow.show();
    }

    private void fireCalcBtn() {
	resultField.setText("");

		int minLen;
		int eMin;
		int eMax;
		try {
			minLen = Integer.parseInt(patSize.getText());
			eMin = Integer.parseInt(extMin.getText());
	        eMax = Integer.parseInt(extMax.getText());
	        if (eMin > eMax) throw new NumberFormatException();

		} catch (NumberFormatException e) {
			final Alert alert = new Alert(AlertType.ERROR);

            alert.setTitle("Pattern Finder");
            alert.setHeaderText("Invalid Lengths");
            alert.setContentText("Please check the pattern and extend lengths");
            alert.showAndWait();
            return;
		}

		final ArrayList<Single> singleTasks = new ArrayList<>();
		final ArrayList<Triple> tripTasks = new ArrayList<>();
		final ArrayList<String> xtndTasks = new ArrayList<>();
		final StringBuilder errorsString = new StringBuilder();

		final String[] text = codesField.getText().trim().split("\\r?\\n");

		for (int i = 0; i < text.length; i++) {
			final String line = PatUtils.tripleTrimmer(text[i].split("//")[0]);
			final Optional<Single> singLine = Single.create(line);
			final Optional<Triple> tripLine = Triple.create(line);

			if (PatUtils.xtndValidate(line)) {
				xtndTasks.add(text[i].replace("+", ""));

			} else if(singLine.isPresent()) {
				final Single code = singLine.get();
			final Either<InvalidCodeSequence, ClassifiedCodeSequence> codeSeq
								= ClassifiedCodeSequence.create(code.getCode());
				if (codeSeq.isRight()) {
					if (correctType(codeSeq.get().codeType))
				singleTasks.add(code);
				}
			} else if(tripLine.isPresent()) {
				final Triple code = tripLine.get();
				boolean valid = true;
				for (int j = 0; j < 3; j++) {
					final ImmutableIntList codePart = code.getCode(j);
			final Either<InvalidCodeSequence, ClassifiedCodeSequence> codeSeq
								= ClassifiedCodeSequence.create(codePart);
				if (codeSeq.isRight()) {
					if (!correctType(codeSeq.get().codeType)) {
						valid = false;
					}
				}
				}
				if (valid) {
					tripTasks.add(code);
				}
			} else {
				if (!line.trim().isEmpty()) {
					errorsString.append(i + 1).append(", ");
				}
			}
		}

		// The pattern finder can emit many lines. Accumulate into one buffer so
		// each append preserves the existing output text without repeatedly
		// copying all prior results.
		final StringBuilder results = new StringBuilder();
		final String singles = singAction(singleTasks, minLen, outputCB.getValue(),
				                          pool, verifyCB.isSelected());
		final String triples = tripAction(tripTasks, minLen, outputCB.getValue(),
				                          pool, verifyCB.isSelected());
		final String extend = xtndAction(xtndTasks, eMin, eMax, pool, verifyCB.isSelected());
		if (errorsString.length() > 0) {
			final String errStr = errorsString.toString() + "~";
			results.append("// errors exist in line(s) ")
					.append(errStr.replace(", ~", ""))
					.append(".\n\n");
		}
		if (!singles.isEmpty()) {
			results.append("//------------------------ SINGLE RESULTS ------------------------//\n");
			results.append(singles);
		}
		if (!triples.isEmpty()) {
			results.append("//------------------------ TRIPLE RESULTS ------------------------//\n");
			results.append(triples);
		}
		if (!extend.isEmpty()) {
			results.append("//------------------------ EXTEND RESULTS ------------------------//\n");
			results.append(extend).append("\n");
		}

		resultBackup = results.toString();
		resultField.setText(results.toString());
    }

    private void fireDownBtn() {

        final StringBuilder results = new StringBuilder();

        final StringBuilder singles = new StringBuilder();
        final StringBuilder triples = new StringBuilder();
        final StringBuilder extend = new StringBuilder();
        final StringBuilder errorsString = new StringBuilder();

        final String[] text = codesField.getText().trim().split("\\r?\\n");

        for (int i = 0; i < text.length; i++) {
            final String line = Utils.tripleTrimmer(text[i].split("//")[0]);
            final Optional<Single> singLine = Single.create(line);
            final Optional<Triple> tripLine = Triple.create(line);

            if (PatUtils.xtndValidate(line) && !line.isEmpty()) {
                extend.append(header((new IntArrayList()).toImmutable(), (new IntArrayList()).toImmutable(), 0)
                        + line.trim() + "\n\n");

            } else if(singLine.isPresent() && !line.isEmpty()) {
                singles.append(header((new IntArrayList()).toImmutable(), Utils.splitString(line).get(), 0)
                        + line.trim() + "\n\n");

            } else if(tripLine.isPresent() && !line.isEmpty()) {

                triples.append(header((new IntArrayList()).toImmutable(), (new IntArrayList()).toImmutable(), 0)
                        + line.trim() + "\n\n");

            } else {
                if (!line.trim().isEmpty()) {
                    errorsString.append(i + 1).append(", ");
                }
            }
        }

        if (errorsString.length() > 0) {
            final String errStr = errorsString.toString() + "~";
            results.append("// errors exist in line(s) ")
                    .append(errStr.replace(", ~", ""))
                    .append(".\n\n");
        }
        if (singles.length() > 0) {
            results.append("//------------------------ SINGLE RESULTS ------------------------//\n");
            results.append(singles).append("\n\n");
        }
        if (triples.length() > 0) {
            results.append("//------------------------ TRIPLE RESULTS ------------------------//\n");
            results.append(triples).append("\n\n");
        }
        if (extend.length() > 0) {
            results.append("//------------------------ EXTEND RESULTS ------------------------//\n");
            results.append(extend).append("\n\n");
        }

        final String resultsStr = results.toString().trim();
        resultBackup = resultsStr;
        resultField.setText(resultsStr);

    }

    private void fireSuperCheckBtn(final boolean showProgress) {
		if (resultBackup.trim().isEmpty()) return;

        final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
		final String[] blocks = resultBackup.trim().split("\\r?\\n\\r?\\n");

		final SuperCheckTask task = new SuperCheckTask(resultBackup, checkLRsCB.isSelected(),
		                                          Integer.parseInt(checkLenField.getText()), pool);
		final Progress progress;
		if (showProgress) {
			progress = new Progress(task);
		} else {
			progress = null;
		}

		task.setOnSucceeded(e -> {
		try {

			final String result = task.get();
			final String[] newBlocks = result.trim().split("\\r?\\n\\r?\\n");

				resultField.setText(result);
				resultBackup = result;

				final int removed;
				if (result.trim().equals("")) {
					// we removed everything but newBlocks still has a size of 1 (not 0), so the
					// removed would have been one less than it should be.
					removed = blocks.length;
				} else {
					removed = blocks.length - newBlocks.length;
				}

				if (showProgress) {
					final Alert alert = new Alert(AlertType.INFORMATION);
		            alert.setTitle("Super Check");
		            alert.setHeaderText("Super Check");
		            alert.setContentText(String.format("Removed %d pattern(s).", removed));
		            alert.showAndWait();
				} else {
					fireCleanBtn();
				}

			} catch (InterruptedException e1) {
			System.out.println("Interrupted Exeption in Super Checker");
			} catch (ExecutionException e1) {
			System.out.println("Execution Exeption in Super Checker");
			}
		if (showProgress) {
		progress.close();
		}
            executor.shutdown();

		});

		task.setOnCancelled(e -> {
             executor.shutdown();
		});

        task.setOnFailed(e -> {
            System.out.println("Something went wrong");
            if (showProgress) {
		progress.close();
		}
            executor.shutdown();
        });

        executor.execute(task);
        if (showProgress) {
		progress.show();
		}
    }

    private void fireExtendBtn() {
		final String[] text = codesField.getText().trim().split("\\r?\\n");

		final StringBuilder newTxt = new StringBuilder();

		for (int i = 0; i < text.length; i++) {
			if (PatUtils.xtndValidate(text[i]) && !text[i].contains("+")) {
				newTxt.append("+").append(text[i].trim()).append("\n");
			} else {
				newTxt.append(text[i]).append("\n");
			}
		}
		codesField.setText(newTxt.toString());
    }

    private void fireCleanBtn() {
		final StringBuilder singText = new StringBuilder();
		final StringBuilder tripText = new StringBuilder();

		// split on the blocks of results
		final String[] blocks = resultField.getText().trim().split("\\r?\\n\\r?\\n");

		for (int block = 0; block < blocks.length; block++) {
			if (blocks[block].contains("empty")) {
				continue;
			}
			final String[] text = blocks[block].split("\\r?\\n");

			for (int i = 0; i < text.length; i++) {
			if (!text[i].contains("//") && !text[i].contains("+")
									&& !text[i].trim().isEmpty()) {
				if (text[i].contains(",") && !text[i].contains(")")) {
					//triple
					tripText.append(text[i].trim()).append("\n");
				} else {
					//single
					if (text[i].contains(")")) {
						singText.append(text[i].split(Pattern.quote(")"))[1].trim()).append("\n");
					} else {
						singText.append(text[i].trim()).append("\n");
					}
				}

			}
		}
		}

		resultField.setText(singText.toString() + "\n" + tripText.toString());

    }


    private boolean correctType(final CodeType type) {
	if (type.equals(CodeType.OSNO) && OSNOcb.isSelected()) {
		return true;
	} else if (type.equals(CodeType.OSO) && OSOcb.isSelected()) {
		return true;
	} else if (type.equals(CodeType.CS) && CScb.isSelected()) {
		return true;
	} else if (type.equals(CodeType.ONS) && ONScb.isSelected()) {
		return true;
	} else if (type.equals(CodeType.CNS) && CNScb.isSelected()) {
		return true;
	} else {
		return false;
	}
    }

    private static String xtndAction(ArrayList<String> lines,final int eMin, final int eMax,
								 final ConnectionPool pool, final boolean verify) {

	final StringBuilder result = new StringBuilder();

	for (String line : lines) {
		final String codeStr = Utils.tripleTrimmer(line.split("#")[0].trim());
		final String patStr = line.split("#")[1].trim();

            final int extlen = eMax - eMin;

		if (codeStr.contains(",")) {
			// triple
			final String[] codes = codeStr.split(",");
			final String[] pats = patStr.split(",");
			if (codes.length != 3 || pats.length != 3) {
				return "";
			}

			result.append("// pat: " + patStr);
				result.append("\n// base: " + codeStr);
				result.append("\n// n = " + eMin + " -> " + eMax + "\n");
			final StringBuilder[] results = new StringBuilder[extlen];
			for (int r = 0; r < extlen; r++) {
				results[r] = new StringBuilder();
			}

			for (int pos = 0; pos < 3; pos++) {
			final Optional<ImmutableIntList> code = Utils.splitString(codes[pos]);
			final Optional<ImmutableIntList> pat = Utils.splitString(pats[pos]);

			if (code.isPresent() && pat.isPresent()) {
				for (int i = eMin; i < eMax; i++) {
					final ImmutableIntList newCode = PatUtils.addImm(
							code.get(), pat.get(), i);
					if (verify && !PatUtils.emptyVerify(newCode, pool)) {
					results[i - eMin].append("empty ");
				}
					results[i - eMin].append(PatUtils.printImm(newCode));
					if (pos < 2) {
						results[i - eMin].append(", ");
					} else {
						results[i - eMin].append(" # ").append(patStr.trim()).append("\n");
					}
				}
			}
			}

			final StringBuilder codeString = new StringBuilder();
			for (int i = 0; i < results.length; i++) {
				if (results[i].toString().contains("empty")) {
					codeString.append("// ");
					codeString.append(results[i]);
				break;
				}
				codeString.append(results[i]);
			}
			result.append(codeString.toString() + "\n");
		} else {
			// single
			final Optional<ImmutableIntList> code = Utils.splitString(codeStr);
			final Optional<ImmutableIntList> pat = Utils.splitString(patStr);

			if (code.isPresent() && pat.isPresent()) {
				result.append("// pat: " + PatUtils.printImm(pat.get()));
				result.append("\n// base: " + PatUtils.printImm(code.get()));
				result.append("\n// n = " + eMin + " -> " + eMax + "\n");
				final StringBuilder codeString = new StringBuilder();

				for (int i = eMin; i < eMax; i++) {
					final ImmutableIntList newCode = PatUtils.addImm(
							code.get(), pat.get(), i);
				final Optional<String> standard = Utils.standard(newCode, i);
				if (standard.isPresent()) {
					if (verify && !PatUtils.emptyVerify(newCode, pool)) {
					codeString.append("//empty set ").append(PatUtils.printImm(newCode));
					break;
				} else {
					codeString.append(standard.get());
				}

				} else {
					// we have an invalid code sequence
					codeString.append("// (Invalid) ").append(PatUtils.printImm(newCode));
					break;
				}
				codeString.append(" # ").append(PatUtils.printImm(pat.get())).append("\n");
				}
				result.append(codeString).append("\n");
			}
		}
	}
	return result.toString();
    }

    private static String singAction(ArrayList<Single> lines, final int minLen,
		final String printing, final ConnectionPool pool, final boolean verify) {

	final StringBuilder result = new StringBuilder();

	final LinkedHashMap<Spattern, MutableSet<Single>> map = new LinkedHashMap<>();
	for (int i = 0; i < lines.size() - 1; i++) {
		for (int j = i+1; j < lines.size(); j++) {
			if (lines.get(i).isEmpty() || lines.get(i).isEmpty()) {
				continue;
			}
		final Optional<Spattern> optPat = subtCodes(lines.get(i).getCode(), lines.get(j).getCode());

		if (optPat.isPresent()) {
			Spattern pat = optPat.get();
				if (map.containsKey(pat)) {
				map.get(pat).addAll(Arrays.asList(lines.get(i), lines.get(j)));
			} else {
				map.put(pat, new UnifiedSet<>(Arrays.asList(lines.get(i), lines.get(j))));
			}
		}
	}
	}

	for (Spattern pat : map.keySet()) {
		if (map.get(pat).size() >= minLen) {
	            final MutableSortedSet<Single> patSet = map.get(pat).toSortedSet();
	            if (patSet.size() < minLen) {
		// there were repeats
		continue;
	            }
			final StringBuilder patString = new StringBuilder();
			patString.append(pat).append("\n// n = ");
			final StringBuilder codeString = new StringBuilder();
			final MutableIntList ns = new IntArrayList();

			for (Single code : patSet) {
				final int n = pat.getN(code);
			ns.add(n);
			if (printing.equals("List All")) {
				final Optional<String> standard = Utils.standard(code.getCode(), n);
				if (standard.isPresent()) {
					if (verify && !PatUtils.emptyVerify(code.getCode(), pool)) {
						codeString.append("//empty set ").append(PatUtils.printImm(code.getCode())).append("\n");
				} else {
					codeString.append(standard.get()).append("\n");
				}

				} else {
					// we have an invalid code sequence
					codeString.append("// (Invalid) ").append(PatUtils.printImm(code.getCode())).append("\n");
				}
			}
			}
			if (printing.equals("Iteration: Start")) {
				codeString.append("+").append(PatUtils.printImm(patSet.min().getCode()))
				                  .append(" # ").append(PatUtils.printPat(pat.getPat())).append("\n");
			} else if (printing.equals("Iteration: Base")) {
				codeString.append("+").append(PatUtils.printImm(pat.getBase()))
	                  .append(" # ").append(PatUtils.printPat(pat.getPat())).append("\n");
			} else if (printing.equals("Iteration: End")) {
				codeString.append("+").append(PatUtils.printImm(patSet.max().getCode()))
	                  .append(" # ").append(PatUtils.printPat(pat.getPat())).append("\n");
			}
			patString.append(createNs(ns));
	result.append(patString).append("\n").append(codeString).append("\n");
		}
	}
	return result.toString();
    }

    // for some reason map.keySet().contains(key) was not working on the Tpatterns, and I gave up
    // staring at it and wrote this function instead.
    private static Optional<Tpattern> keySetContains(
		LinkedHashMap<Tpattern, MutableSet<Triple>> map, Tpattern key) {
	for (Tpattern item : map.keySet()) {
		if (item.equals(key)) {
			return Optional.of(item);
		}
	}
	return Optional.empty();
    }


    private static String tripAction(ArrayList<Triple> lines, final int minLen, final String printing,
		                         final ConnectionPool pool, final boolean verify) {

	final StringBuilder result = new StringBuilder();
	final LinkedHashMap<Tpattern, MutableSet<Triple>> map = new LinkedHashMap<>();
	for (int i = 0; i < lines.size() - 1; i++) {
		for (int j = i+1; j < lines.size(); j++) {
		final Optional<Tpattern> optPat = subtCodes(lines.get(i), lines.get(j));
		if (optPat.isPresent()) {
			Tpattern pat = optPat.get();
			// if Keyset.Contains wasn't working and I gave up trying, so now we do this:
			final Optional<Tpattern> opt = keySetContains(map, pat);
			if (opt.isPresent()) {
				map.get(opt.get()).addAll(Arrays.asList(lines.get(i), lines.get(j)));
			} else {
				map.put(pat, new UnifiedSet<>(Arrays.asList(lines.get(i), lines.get(j))));
			}
		}
	}
	}
	for (Tpattern pat : map.keySet()) {
		if (map.get(pat).size() >= minLen) {
	            final MutableSortedSet<Triple> patSet = map.get(pat).toSortedSet();
	            if (patSet.size() < minLen) {
		// there were repeats
		continue;
	            }
			final StringBuilder patString = new StringBuilder();
			patString.append(pat).append("\n// n = ");
			final StringBuilder codeString = new StringBuilder();
			final MutableIntList ns = new IntArrayList();

		for (Triple code : patSet) {
			final int n = pat.getN(code);
			ns.add(n);
			if (printing.equals("List All")) {
				if (verify) {
					final String tPrinted = PatUtils.printAndTestTrip(code, pool);
					if (tPrinted.contains("empty") || tPrinted.contains("Invalid")) {
						codeString.append("// ").append(tPrinted).append("\n");
						break;
					}
					codeString.append(tPrinted).append("\n");
				} else {
					codeString.append(code).append("\n");
				}
			}
		}

			if (printing.equals("Iteration: Start")) {
				codeString.append("+").append(patSet.min()).append(" # ").append(pat.patString()).append("\n");

			} else if (printing.equals("Iteration: Base")) {
				codeString.append("+").append(pat.baseString()).append(" # ").append(pat.patString()).append("\n");

			} else if (printing.equals("Iteration: End")) {
				codeString.append("+").append(patSet.max()).append(" # ").append(pat.patString()).append("\n");
			}

			patString.append(createNs(ns));
	result.append(patString.toString().replace(", ~", "")).append("\n").append(codeString).append("\n");
		}
	}
	return result.toString();
    }


    private static String header(final ImmutableIntList pat, final ImmutableIntList code, final int num) {
        final StringBuilder result = new StringBuilder();
	result.append("// pat: ").append(PatUtils.printImm(pat));
	result.append("\n// base: ").append(PatUtils.printImm(code));
	result.append("\n// n = 0 -> ").append(num).append("\n");

	return result.toString();
    }


    // just makes a string to display which elements of a pattern are present
    private static String createNs(MutableIntList ns) {
	final StringBuilder result = new StringBuilder();
	result.append(ns.getFirst());

	for (int i = 1; i < ns.size(); i++) {
		if (ns.get(i) == 1 + ns.get(i - 1)) {
			if (!result.toString().endsWith(" -> ")) {
				result.append(" -> ");
			}
		} else {
			if (result.toString().endsWith(" -> ")) {
				result.append(ns.get(i - 1));
			}
			result.append(", ").append(ns.get(i));
		}
	}

	if (result.toString().endsWith(" -> ")) {
			result.append(ns.getLast());
		}

	return result.toString();
    }

    private static Optional<Tpattern> subtCodes(Triple t1, Triple t2) {
	final Tpattern result = new Tpattern();
	if (Triple.compare(t1, t2) != 0) {
		return Optional.empty();
	}
		int negative = 0;
	for (int i = 0; i < 3; i++) {
		final ImmutableIntList c1 = t1.getCode(i);
		final ImmutableIntList c2 = t2.getCode(i);
		if (negative == 0) {
			negative = PatUtils.intListCompare(c1, c2);
		} else {
			if (negative != PatUtils.intListCompare(c1, c2)) {
				return Optional.empty();
			}
		}
		final Optional<Spattern> pat = subtCodes(c1, c2);

		if (!pat.isPresent()) {
			return Optional.empty();
		}
		result.setPat(pat.get(), i);
	}
	result.setBase(t1.getCodes());
	return Optional.of(result);
    }

    // subtracts lines from each other. Returns a ImmutableIntList of the proper pattern,
    // or empty if the codes are incompatible.
    private static Optional<Spattern> subtCodes(ImmutableIntList line1, ImmutableIntList line2) {

		if (line1.size() != line2.size() || line1.equals(line2)) {
			return Optional.empty();
		}

		ThreeState negative = ThreeState.UNSET;
		final int[] result = new int[line1.size()];
		for (int i = 0; i < line1.size(); i++) {
			int value = line1.get(i) - line2.get(i);
			if (value != (value / 2) * 2) {
				return Optional.empty();
			}
			if (value == 0) {
				result[i] = value;
			}
			if (value < 0) {
				if (negative == ThreeState.FALSE) {
					return Optional.empty();
				}
				negative = ThreeState.TRUE;

				for (int j=0; j < -value/2; j++) {
					result[i] = -value / 2;
				}

			} else if (value > 0) {
				if (negative == ThreeState.TRUE) {
					return Optional.empty();
				}
				negative = ThreeState.FALSE;

				for (int j=0; j < value/2; j++) {
					result[i] = value / 2;
				}
			}
		}
		return Optional.of(new Spattern(PatUtils.listGCD(result), line1));
    }
}
