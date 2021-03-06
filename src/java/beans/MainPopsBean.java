/* 
 * Copyright 2016 University of Adelaide.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package beans;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.component.UIComponent;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.http.HttpServletRequest;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.primefaces.component.chart.Chart;
import org.primefaces.component.datagrid.DataGrid;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.component.dialog.Dialog;
import org.primefaces.component.spacer.Spacer;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.chart.BarChartModel;
import org.primefaces.model.menu.DefaultMenuModel;
import poplogic.ChartModelWithId;
import poplogic.ExpressionData;
import poplogic.Gene;
import poplogic.PerLocationContigs;
import reusable.BlastResults;
import reusable.Hit;
import reusable.HitDataModel;
import reusable.HitsForQuery;
import reusable.HitsForQueryDataModel;
import reusable.InReader;
import reusable.QesHits;
import reusable.Sequence;
import reusable.TempFilesCleaner;
//import org.primefaces.model.DefaultMenuModel;
//import org.primefaces.model.chart.DonutChartModel;

/**
 *
 * @author Radoslaw Suchecki <radoslaw.suchecki@adelaide.edu.au>
 */
@ManagedBean(name = "mainBean")
@ViewScoped
public class MainPopsBean implements Serializable {

    private boolean autoDisplayCharts = true;

    private String currentChromosome;
    private ArrayList<Gene> loadedGenes;
    private ArrayList<Gene> selectedGenes;
    private ArrayList<Gene> selectedGenesForChartDisplay;
    private Location_cMFilter cM_filter;

    private String userQuery;
    private String userQueryInternal;
//    private String userQuerySeq;
    private String globalFilter;

    //chart-dialog related
    private Gene geneSelectedForDialogDisplay;
    private final static String DIALOG_CONTAINERS_PARENT = "formCentre";
    private final static int DIALOGS_MAX_NUMBER = 15;
    private final static int DIALOG_WIDTH = 480; //450;
    private final static int DIALOG_HEIGHT = 280; //250;
//    protected HashMap<String, Dialog> dialogIdToDialogMap = new HashMap<>();
    protected HashMap<String, Dialog> geneIdToDialogMap = new HashMap<>();
    protected ArrayList<UIComponent> availableDialogContainers = new ArrayList<>();

    //menu model for selecting chromosome to display 
    private DefaultMenuModel menuModel; //multi level e.g. chromosome 1 -> submenu 1{A,B,D}
    private DefaultMenuModel menuModelSimple; //Single level menu e.g. 1A,1B,1D
//    private DefaultMenuModel menuModelForOtherContigs; //multi level chromosome 1 -> submenu 1{A,B,D} -> range

    private static final int BUFFER_SIZE = 6124;
    private String fileContentString;
    private ArrayList<Sequence> sequences;

    private StreamedContent exportFileWholeIWGSC;
    private String toExport = "page";

    //display of non-gene contigs
    private PerLocationContigs perLocationContigs;
    private String chromosomeForNonGeneContigs;

    //display unordered genes
    private boolean appendUnordered;

    //BLASTn alignment handling and retrieval
    private QesHits blastn;

    @ManagedProperty(value = "#{appDataBean}")
    private AppDataBean appData;

    public MainPopsBean() {
        perLocationContigs = new PerLocationContigs(null, new Location_cMFilter());
        cM_filter = new Location_cMFilter();
    }

    public void setAppData(AppDataBean appData) {
        this.appData = appData;
        this.perLocationContigs.setAppData(appData);
    }

    private boolean initFilterTable;

    public boolean isInitFilterTable() {
        return initFilterTable;
    }

    public void setInitFilterTable(boolean initFilterTable) {
        this.initFilterTable = initFilterTable;
    }

    @PostConstruct
    public void init() {
        blastn = new QesHits(appData.getBLAST_DIR());
//        if (!FacesContext.getCurrentInstance().isPostback()) {
//            RequestContext.getCurrentInstance().execute("alert('This onload script is added from backing bean.')");
//        }
//        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        Map<String, String> parameterMap = (Map<String, String>) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        if (!parameterMap.isEmpty()) {
            RequestContext context = RequestContext.getCurrentInstance();
            if (parameterMap.containsKey("query")) {
                String id = parameterMap.get("query").trim().replaceFirst("\\.\\d+$", "");
                setUserQuery(id);
                searchAll(id, ":formSearch:searchMessages");
                context.update(":formSearch:idInput,:formSearch:searchMessages,:formCentre:dataTable,:formCentre:chartsGrid,:formSearch3:contigList");
            }
            if (parameterMap.containsKey("blastn")) {
                String key = parameterMap.get("blastn").trim();
                perQueryResults = blastn.retrieveHits(key, appData.getBLAST_DB());
                sequences = blastn.getRetrievedSequences();
                fileContentString = blastn.getRetrievedSequencesString();
                processRetrievedResults();
//                RequestContext context = RequestContext.getCurrentInstance();
//                context.execute("PF('searchSeqPanel').show()");
            }
            if (parameterMap.containsKey("chromosome")) {
                String chromosome = parameterMap.get("chromosome").trim();

                if (isChromosomeLabel(chromosome)) {
                    if (parameterMap.containsKey("cM")) {
                        initFilterTable = true;
                        String cM = parameterMap.get("cM");
                        String range[] = cM.trim().split("-");
                        try {
                            cMLoadExample(chromosome, Double.parseDouble(range[0]), Double.parseDouble(range[1]));
                            growl(FacesMessage.SEVERITY_INFO, "Chromosome region loaded", "Chromosome "+chromosome+" "+cM+" cM", "searchMessages2");

//                        RequestContext.getCurrentInstance().execute("filterTable();");
//                        RequestContext.getCurrentInstance().execute("PF('block2Table').show();PF('dataTable').filter();PF('block2Table').hide();");
                        } catch (ArrayIndexOutOfBoundsException | NumberFormatException arr) {
                            growl(FacesMessage.SEVERITY_WARN, "Invalid GET request parameter", cM + " could not be parsed, try e.g. ?chromosome=5B&cM=14.798.5-16.987", "searchMessages2");
                        }
                    } else {
                        onSelect(chromosome);
                    }
                } else {
                    growl(FacesMessage.SEVERITY_WARN, "Invalid GET request parameter", chromosome + " is not a valid wheat chromosome name, try e.g. ?chromosome=5B&cM=14.798.5-16.987", "searchMessages2");
                }
            }
        }

//        System.err.println("AppData size="+appData.getContigs("1A").size());
    }

    public PerLocationContigs getPerLocationContigs() {
        return perLocationContigs;
    }

    public String getChromosomeForNonGeneContigs() {
        return chromosomeForNonGeneContigs;
    }

    public void setChromosomeForNonGeneContigs(String chromosomeForNonGeneContigs) {
        this.chromosomeForNonGeneContigs = chromosomeForNonGeneContigs.toUpperCase();
    }

//    public void updateBLASTn() {
//        Date date = new Date(System.currentTimeMillis());
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-EEE-HHmmss");
//        String key = dateFormat.format(date) + "-" + Math.random();
//        System.err.println("Current key: "+key);
//        blastn = new QesHits();
//            updateComponent(":formSearch2:blockBLAST:linkBLAST");
//    }
    public void handleFileUpload(FileUploadEvent event) {
        ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
//        System.out.println(event.getFile().getFileName()+" in "+System.getProperty("java.io.tmpdir"));

//        File result = new File(extContext.getRealPath("//WEB-INF//uploaded//" + event.getFile().getFileName()));
//        System.out.println(extContext.getRealPath("//WEB-INF//uploaded//" + event.getFile().getFileName()));
        try {
            File tempFile = File.createTempFile(event.getFile().getFileName(), ".fa");
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[BUFFER_SIZE];
            int contentsI;
            InputStream inputStream = event.getFile().getInputstream();
            while (true) {
                contentsI = inputStream.read(buffer);
                if (contentsI < 0) {
                    break;
                }
                fileOutputStream.write(buffer, 0, contentsI);
                fileOutputStream.flush();
            }

            fileOutputStream.close();
            inputStream.close();

            String fasta = InReader.readInputToString(tempFile.toString());

            if (setFileContentStringValidate(fasta)) {
                growl(FacesMessage.SEVERITY_INFO, "File:", event.getFile().getFileName() + " successfully uploaded.", "searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Size:", reusable.CommonMaths.round((double) event.getFile().getSize() / 1024, 2) + " kB", "searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Number of sequences:", "" + sequences.size(), "searchMessages2");
            }
        } catch (IOException e) {
            e.printStackTrace();
            growl(FacesMessage.SEVERITY_FATAL, "The file was not uploaded!", "", "searchMessages2");
        } finally {
//            result.delete();
        }
    }

    public QesHits getBlastn() {
//        if (blastn == null) {
//            blastn = new QesHits();
//        }
        return blastn;
    }

    public String getBLASTnKey() {
        if (blastn != null) {
            return blastn.getKey();
        }
        return null;
    }

    public void setBlastn(QesHits blastn) {
        this.blastn = blastn;
    }

    public boolean setFileContentStringValidate(String fileContentString) {
        if (isValidFasta(fileContentString)) {
            this.fileContentString = fileContentString;
            if ((fileContentString == null || fileContentString.isEmpty()) && getGlobalFilter().equals(fileContentString)) {
                setGlobalFilter("");
            }
//            blastn = new QesHits();
//            updateComponent(":formSearch2:blockBLAST:linkBLAST");
            return true;
        } else if (fileContentString.matches("[ACTGWSMKRYBDHVNactgwsmkrybdhvn\\r\\n]+")) {
            this.fileContentString = ">unlabelled_sequence\n" + fileContentString;
            sequences = reusable.FastaOps.sequencesFromFastaString(this.fileContentString, true);
            return true;
        } else {
            this.fileContentString = null;
            if ((fileContentString == null || fileContentString.isEmpty()) && getGlobalFilter().equals(fileContentString)) {
                setGlobalFilter("");
            }
            growl(FacesMessage.SEVERITY_ERROR, "Error!", "Not a valid FASTA input!", "searchMessages2");
            return false;
        }

    }

    public void clearFileContentString() {
        this.fileContentString = null;
        growl(FacesMessage.SEVERITY_INFO, "Cleared!", "", "searchMessages2");
        setSeqSearchTabActive("0");
    }

    public void setFileContentString(String fileContentString) {
        if (fileContentString == null || fileContentString.trim().isEmpty()) {
            this.fileContentString = "";
        } else {
            setFileContentStringValidate(fileContentString);
        }
    }

    public String getFileContentString() {
//        blastn = new QesHits();
//        updateComponent(":formSearch2:blockBLAST:linkBLAST");
        return fileContentString;
    }

    private boolean isValidFasta(String fileContents) {
        boolean valid = false;
        try {
            sequences = reusable.FastaOps.sequencesFromFastaString(fileContents, true);
            if (sequences != null && !sequences.isEmpty()) {
                for (Sequence s : sequences) {
                    if (s.getIdentifierString().length() == 0) {
                        return false;
                    }
                    if (s.getLength() == 0) {
                        return false;
                    } else if (s.containsNonIUPAC()) {
                        return false;
                    }
                }
                valid = true;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return valid;
    }

    public String getCurrentChromosome() {
        if (currentChromosome == null) {
            return "";
        }
        return "Genes on POPSEQ-anchored contigs (chromosome " + currentChromosome + ")";
    }

    public void setCurrentChromosome(String currentChromosome) {
        this.currentChromosome = currentChromosome;
    }

    public void searchAll(ActionEvent actionEvent) {
        searchAll(userQuery, ":formSearch:searchMessages");
    }
    public void searchAllInternal(String userQuery) {
        setUserQuery(userQuery);
        searchAll(userQuery, "growl");
    }

    private void searchAll(String userQuery, String messageComponent) {
        appendUnordered = true;
        RequestContext.getCurrentInstance().getCallbackParams().put("showContigList", false);

        if (userQuery == null || userQuery.isEmpty()) {
            growl(FacesMessage.SEVERITY_FATAL, "Searching for nothing?!", "Consider inputting an identifier before clicking 'Search'", messageComponent);
        } else {
//
            String[] queries = userQuery.trim().split(" |,|\n|\t|;");
//
            if (queries.length < 2) {
                SearchResult result = appData.quickFind(userQuery.trim());
                if (result == null || (result.getGene() == null && result.getContig() == null)) {
                    growl(FacesMessage.SEVERITY_FATAL, "Search failed.", "Query not found among POPSeq ordered and/or gene containing contigs", messageComponent);
                } else if (!result.getContig().hasGenes()) {
                    chromosomeForNonGeneContigs = result.getChromosome();
                    loadAllContigs();
                    growl(FacesMessage.SEVERITY_INFO, "\"Query found", userQuery + " found on chromosome " + chromosomeForNonGeneContigs + ", unfortunatelly no annotation or expression data is available for this contig.", messageComponent);
                    RequestContext.getCurrentInstance().getCallbackParams().put("showContigList", true);
                    final DataTable d = (DataTable) FacesContext.getCurrentInstance().getViewRoot().findComponent(":formSearch3:contigList");
                    Integer rowIndex = perLocationContigs.getIndexOfContig(userQuery);

                    //if setFirst is called with an index other than the first row of a page it obscures some of the preceeding rows
                    int rows = d.getRows();
                    int page = rowIndex / rows;
                    d.setFirst(rows * page);
                } else {
                    onSelect(result.getChromosome());
                    final DataTable d = (DataTable) FacesContext.getCurrentInstance().getViewRoot().findComponent(":formCentre:dataTable");
                    Integer rowIndex = result.getIndex();

                    //if setFirst is called with an index other than the first row of a page it obscures some of the preceeding rows
                    int rows = d.getRows();
                    int page = rowIndex / rows;
                    d.setFirst(rows * page);

                    growl(FacesMessage.SEVERITY_INFO, "Query found: ", userQuery + " found on chromosome " + currentChromosome, messageComponent);
                    RequestContext.getCurrentInstance().update(":formCentre:dataTable");
//                    RequestContext context = RequestContext.getCurrentInstance(); 
//                    context.scrollTo("formCentre:dataTable");
//                    context.update("formCentre:dataTable");
                }
            } else {
                growl(FacesMessage.SEVERITY_WARN, "Unfortunatelly", "Not able to process multiple queries", messageComponent);
                RequestContext.getCurrentInstance().update(":formCentre:dataTable");
            }
        }
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
//        System.err.println("Setting user query to "+userQuery);
        if ((userQuery == null || userQuery.isEmpty()) && getGlobalFilter().equals(userQuery)) {
            setGlobalFilter("");
        }
        this.userQuery = userQuery;
    }

    

    public String getGlobalFilter() {
        if (globalFilter != null) {
            return globalFilter;
        } else {
            return "";
        }
    }

    public void setGlobalFilter(String globalFilter) {
        this.globalFilter = globalFilter;
    }

    public Gene getGeneSelectedForDialogDisplay() {
        return geneSelectedForDialogDisplay;
    }

    public void setGeneSelectedForDialogDisplay(Gene geneSelectedForDialogDisplay) {
        this.geneSelectedForDialogDisplay = geneSelectedForDialogDisplay;
//        currentContainerId = generateChartDialog();
        generateChartDialog();
    }
    private String currentContainerId;

    public String getCurrentContainerId() {
        return currentContainerId;
    }

    public void generateDialogContainers() {
        if (availableDialogContainers == null || availableDialogContainers.isEmpty()) {
            ComponentGenerator componentGenerator = new ComponentGenerator();
            availableDialogContainers = componentGenerator.generateDialogContainers(0, DIALOGS_MAX_NUMBER, DIALOG_CONTAINERS_PARENT);
        }

    }

    public void growl(FacesMessage.Severity severity, String header, String content, String messageOrGrowlComponent) {
        if (severity == null) {
            severity = FacesMessage.SEVERITY_INFO;
        }
        FacesContext context = FacesContext.getCurrentInstance();
        context.addMessage(messageOrGrowlComponent, new FacesMessage(severity, header, content));
        RequestContext.getCurrentInstance().update(messageOrGrowlComponent);
    }

    private void generateChartDialog() {
        Dialog dlg = geneIdToDialogMap.get(geneSelectedForDialogDisplay.getGeneId());
        if (dlg != null) {
            growl(FacesMessage.SEVERITY_WARN, "Note!", "Expression chart already opened for " + geneSelectedForDialogDisplay.getGeneId(), "growl");
            //for now re-draw
            RequestContext.getCurrentInstance().update(dlg.getParent().getId());
//            return null;
        } else if (availableDialogContainers.isEmpty()) {
            growl(FacesMessage.SEVERITY_WARN, "Getting crowded", "You have reached the maximum number of " + DIALOGS_MAX_NUMBER + " charts displayed simultaneously. Close some before openning more.", "growl");
//            return null;
        } else {
            UIComponent container = availableDialogContainers.remove(availableDialogContainers.size() - 1);
            String currentDisplay = DIALOG_CONTAINERS_PARENT + ":" + container.getId();

            ComponentGenerator componentGenerator = new ComponentGenerator();
            Dialog dialog = componentGenerator.generateDialog(geneSelectedForDialogDisplay, geneIdToDialogMap, DIALOG_WIDTH, DIALOG_HEIGHT, availableDialogContainers);
            container.getChildren().add(dialog);
//            containerId = container.getClientId().split("_")[1];
            String suffix = container.getClientId().split("_")[1]; //for no good reason using the same suffix for component identifiers

            ArrayList<ChartModelWithId> models = geneSelectedForDialogDisplay.getBarChartModels();
            for (int i = 0; i < models.size(); i++) {
                BarChartModel barChartModel = models.get(i);
                Chart chart = componentGenerator.generateChart(suffix + i, DIALOG_WIDTH, DIALOG_HEIGHT, barChartModel);
                dialog.getChildren().add(chart);
                Spacer s = new Spacer();
                s.setId(chart.getId() + "_spacer");
                dialog.getChildren().add(s);
            }

            RequestContext.getCurrentInstance().update(currentDisplay);
        }
    }

    public DefaultMenuModel getMenuModelSimple() {
        if (menuModelSimple != null) {
            return menuModelSimple;
        }

        menuModelSimple = new ComponentGenerator().generateDynamicMenuSingleLevel();
        return menuModelSimple;
    }

    public DefaultMenuModel getMenuModel() {
        if (menuModel != null) {
            return menuModel;
        }
        menuModel = new ComponentGenerator().generateDynamicMenuMultilevel();
        return menuModel;
    }

    public void updateDisplayedContigs() {
        onSelect(currentChromosome);
    }

//    
    public void onSelect(String chromosome) {
//        ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();//        String path = extContext.getRealPath(PATH);
        if (chromosome != null) {
            currentChromosome = chromosome;
            if (appendUnordered) {
                loadedGenes = appData.getGenesAll(chromosome);
//                System.out.println("Loading all " + loadedGenes.size() + " genes");
            } else {
                loadedGenes = appData.getGenesBinned(chromosome);
//                System.out.println("Loading binned " + loadedGenes.size() + " genes");
            }
//            genesTissuesFPKMsMap = appData.getGenesToExpressionMap(); //SUPERFLOUS?
            cM_filter = appData.getLocationFilterGenes(chromosome);
            selectedGenes = null;

//            System.out.println(loadedGenes.size() + " genes loaded");
        } else {
            loadedGenes = null;
        }
        final DataTable d = (DataTable) FacesContext.getCurrentInstance().getViewRoot().findComponent(":formCentre:dataTable");
        d.reset();
    }

    public void reload() {
        if (currentChromosome != null) {
            onSelect(currentChromosome);
        }
    }

    public void loadAllContigs() {
//        String fileName = getInputFilename(chromosomeForNonGeneContigs, false);
//        InputProcessor inputProcessor = new InputProcessor();
//        perLocationContigs = inputProcessor.getContigs(fileName);

        perLocationContigs = new PerLocationContigs(appData.getContigs(chromosomeForNonGeneContigs), chromosomeForNonGeneContigs, appData.getLocationFilterContigs(chromosomeForNonGeneContigs));
        perLocationContigs.setAppData(appData);
    }

    public void restrictContigsWithutGenes() {

    }

    public ArrayList<Gene> getSelectedGenes() {
        return selectedGenes;
    }

    public void setSelectedGenes(ArrayList<Gene> selectedGenes) {
        this.selectedGenes = selectedGenes;
    }

    public void clearSelectedGenes() {
        setSelectedGenes(null);
    }

    public void addSelectedGenesToDisplay() {
        for (Gene gene : selectedGenes) {
            addGeneToDisplay(gene);
        }
    }

    public void addGeneToDisplay(Gene gene) {
        if (selectedGenes != null) {
            if (selectedGenesForChartDisplay == null) {
                selectedGenesForChartDisplay = new ArrayList<>(selectedGenes.size());
            }
            if (!gene.isContainedInList(selectedGenesForChartDisplay)) {
                selectedGenesForChartDisplay.add(gene);
            } else {
                growl(FacesMessage.SEVERITY_WARN, "Chart already displayed for ", gene.getGeneId(), "growl");
            }
        }
    }

    public void removeFromChartDisplay(Gene gene) {
        if (selectedGenesForChartDisplay != null) {
            selectedGenesForChartDisplay.remove(gene);
            if (selectedGenesForChartDisplay.isEmpty()) {
                selectedGenesForChartDisplay = null;
            }
        }
//        RequestContext.getCurrentInstance().scrollTo(":formCentre:chartsGrid");
    }

    public ArrayList<Gene> getSelectedGenesForChartDisplay() {
        return selectedGenesForChartDisplay;
    }

    public void clearSelectedGenesForChartDisplay() {
        setSelectedGenesForChartDisplay(null);
    }

    public void setSelectedGenesForChartDisplay(ArrayList<Gene> selectedGenesForChartDisplay) {
        this.selectedGenesForChartDisplay = selectedGenesForChartDisplay;
    }

    public boolean hasNoGenesSelectedForDisplay() {
        return selectedGenesForChartDisplay == null || selectedGenesForChartDisplay.isEmpty();
    }

    public void resetFilter() {
//        selectedDataModel = loadedDataModel;
        cM_filter.resetFilter();
//        setFilteredGenes(null);
        setSelectedGenes(null);
    }

//    public boolean isFiltered() {
//        return filteredGenes != null;
//    }
    public boolean filterIgnoreCaseContains(Object value, Object filter, Locale locale) {
        String filterText = (filter == null) ? null : filter.toString().trim().toLowerCase();
        if (filterText == null || filterText.isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toString().toLowerCase().trim().contains(filterText);
    }

    public boolean isDataLoaded() {
//        if (selectedDataModel != null) {
        if (loadedGenes != null) {
            return true;
        }
        return false;
    }

    public void cMLoadExample(String chromosome, double from, double to) {
        onSelect(chromosome);
        cM_filter.setcM_min_user(from);
        cM_filter.setcM_max_user(to);
    }

    public Location_cMFilter getcM_filter() {
        return cM_filter;
    }

    public void setcM_filter(Location_cMFilter cM_filter) {
        this.cM_filter = cM_filter;
    }

    public boolean somethingSelected() {
        if (selectedGenes != null && !selectedGenes.isEmpty()) {
            return true;
        }
        return false;
    }

    public void exportWholeIWGSCFile() {
        if (selectedGenes == null || selectedGenes.isEmpty()) {
//            exportFile = null;
            System.err.println("Nothing selected on chromosome " + currentChromosome);
        } else {
//            ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
            String newline = System.getProperty("line.separator");
            StringBuilder sb = new StringBuilder();
            for (Gene c : selectedGenes) {
                sb.append(">");
                sb.append(c.getContig().getId());
                sb.append(newline);
                sb.append(reusable.BlastOps.getCompleteSubjectSequence(c.getContig().getId(), appData.getBLAST_DB()).
                        get(0).getSequenceString()
                );
                sb.append(newline);
            }
            InputStream stream = new ByteArrayInputStream(sb.toString().getBytes());
            this.exportFileWholeIWGSC = new DefaultStreamedContent(stream, "application/txt", "Selected_IWGSC_contigs_" + reusable.CommonMaths.getRandomString() + ".fasta");
        }
    }

    public StreamedContent getExportFileWholeIWGSC() {
        exportWholeIWGSCFile();
        return exportFileWholeIWGSC;
    }

    public void setExportFileWholeIWGSC(StreamedContent exportFileWholeIWGSC) {
        this.exportFileWholeIWGSC = exportFileWholeIWGSC;
    }

    public String getToExport() {
        return toExport;
    }

    public void setToExport(String toExport) {
        this.toExport = toExport;
    }

    public boolean exportPageOnly() {
        if (toExport.equals("page")) {
            return true;
        }
        return false;
    }

    public boolean exportSelectedOnly() {
        if (toExport.equals("selected")) {
            return true;
        }
        return false;
    }

    public boolean exportDsiabled() {
        if (isDataLoaded() && (!exportSelectedOnly() || (exportSelectedOnly() && somethingSelected()))) {
            return false;
        }
        return true;
    }

    public void postProcessXLS(Object document) {
        HSSFWorkbook wb = (HSSFWorkbook) document;
        HSSFSheet sheet = wb.getSheetAt(0);

        Iterator<Row> rowIterator = sheet.rowIterator();
        rowIterator.next();
        ArrayList<ExpressionData> expressionDatasets = appData.getExpressionDatasets();
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            Iterator<Cell> cellIterator = row.cellIterator();
//            Cell firstCell = cellIterator.next();
//            String geneId = firstCell.getStringCellValue();
//            cellIterator = row.cellIterator();
//            System.err.println("id: "+geneId);
            String geneId = null;
//            int j=0;
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                String stringCellValue = cell.getStringCellValue();
                if (stringCellValue != null && !stringCellValue.isEmpty()) {
                    cell.setCellValue(stringCellValue.replaceAll("\\<[^>]*>", "")); //strip off HTML
                }
                if (geneId == null || geneId.isEmpty()) {
                    geneId = cell.getStringCellValue().trim();
                }
//                System.err.println("["+(j++)+"]"+cell.getStringCellValue());
            }
//            ArrayList<Double> fpkms = genesTissuesFPKMsMap.get(geneId);
            for (ExpressionData expressionDataset : expressionDatasets) {
                ArrayList<Double> expressionValues = expressionDataset.getExpressionValues(geneId);
                for (int i = 2; i < expressionValues.size(); i++) {
                    Double fpkmDouble = expressionValues.get(i);
                    Cell createdCell = row.createCell(row.getLastCellNum());
                    createdCell.setCellValue(fpkmDouble);
                }
            }

//            if (fpkms != null && !fpkms.isEmpty()) {
//                for (int i = 2; i < fpkms.size(); i++) {
//                    Double fpkmDouble = fpkms.get(i);
//                    Cell createdCell = row.createCell(row.getLastCellNum());
//                    createdCell.setCellValue(fpkmDouble);
//                }
//            }
//            cellIterator = row.cellIterator();
//            String geneId = cellIterator.next().getStringCellValue();
//            Gene gene = selectedDataModel.getRowData(geneId);
//            ArrayList<Double> tissuesFPKMs = gene.getTissuesFPKMs();
//            for (Double double1 : tissuesFPKMs) {
////                row.i
//            }
        }

        Row row = sheet.getRow(0);
        Iterator<Cell> cellIterator = row.cellIterator();
        String headers[] = appData.getTABLE_HEADERS().split(",");
        int p = 0;
        for (String h : headers) {
//            System.err.println("Adding "+h+" at "+(p++));
            cellIterator.next().setCellValue(h);

        }

        //Add headers for FPKM values
        for (ExpressionData expressionDataset : expressionDatasets) {
            for (int i = 3; i < expressionDataset.getHeader().length; i++) {
                Cell createdCell = row.createCell(row.getLastCellNum());
                createdCell.setCellValue(expressionDataset.getHeader()[i]);
//                createdCell.setCellValue(fpkmTableHeaders[i]);
            }
        }
    }

    public boolean contains(String queries, String elem) {
        String arr[] = null;
        if (queries != null) {
            arr = queries.split("[^_|\\w]"); //split on anything that is not a alphanumeric or an underscore
        }
        for (String string : arr) {
            if (string.equals(elem)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(String queries, String elem, String elem1) {
        String arr[] = null;
        if (queries != null) {
            arr = queries.split("[^_|\\w]"); //split on anything that is not a alphanumeric or an underscore
        }
        for (String string : arr) {
            if (string.equals(elem) || string.equals(elem1)) {
                return true;
            }
        }
        return false;
    }

    //BELOW, BLAST-based ALIGNMEN_SEARCH,CODE RECYCLED FROM BWPF by Rad Suchecki
//    private ArrayList<HitsForQuery> perQueryResults;
    private BlastResults perQueryResults;
    private HitsForQueryDataModel hitsForQueryDataModel;
    private HitDataModel hitsDataModel;
    private HitsForQuery selectedQuery;
    private List<Hit> selectedHits;
    private Hit selectedHit;
    private String seqSearchTabActive = "0";

    public String getSeqSearchTabActive() {
        return seqSearchTabActive;
    }

    public void setSeqSearchTabActive(String seqSearchTabActive) {
        this.seqSearchTabActive = seqSearchTabActive;
    }

    public HitDataModel getHitsDataModel() {
        return hitsDataModel;
    }

    public boolean hasHitsDataModel() {
        return hitsDataModel != null && hitsDataModel.getRowCount() != 0;
    }

    public List<Hit> getSelectedHits() {
        return selectedHits;
    }

    public Hit getSelectedHit() {
        return selectedHit;
    }

    public void setSelectedHit(Hit selectedHit) {
        if (selectedHit != null) {
            this.selectedHit = selectedHit;
            setUserQuery(selectedHit.getHitId());
            searchAll(selectedHit.getHitId(), ":formSearch2:searchMessages2");
        }
    }

//    public ArrayList<HitsForQuery> getPerQueryResults() {
//        return perQueryResults;
//    }
    public BlastResults getPerQueryResults() {
        return perQueryResults;
    }

    public HitsForQueryDataModel getHitsForQueryDataModel() {
        return hitsForQueryDataModel;
    }

    public boolean hasHitsForQueryDataModel() {
        return hitsForQueryDataModel != null && hitsForQueryDataModel.getRowCount() != 0;
    }

    public HitsForQuery getSelectedQuery() {
        if (selectedQuery != null) {
            selectedHits = selectedQuery.getPromotersList(0);
        }
        return selectedQuery;
    }

    public void setSelectedQuery(HitsForQuery selectedQuery) {
        this.selectedQuery = selectedQuery;
        if (selectedQuery != null) {
            hitsDataModel = new HitDataModel(selectedQuery.getHits());
            setSeqSearchTabActive("2");
        }
    }

//    public void blastnLinkEventHandler(ActionEvent actionEvent) {       
//        System.err.println("key "+blastn.getKey());
//        growl(FacesMessage.SEVERITY_INFO, "TESTINFO", "content", "searchMessages2" );
//    }
    public void sequenceSearchEventHandler(ActionEvent actionEvent) {
        Date submitTime = new Date();
        if (sequences != null && !sequences.isEmpty()) {
            QesHits blastn = this.blastn;  //take existing blastn instance
            this.blastn = new QesHits(appData.getBLAST_DIR()); //generate one for the next time
//            RequestContext.getCurrentInstance().getCallbackParams().put("blastkey", blastn.getKey());
//            perQueryResults = new ArrayList<>();
            perQueryResults = new BlastResults(true, null, null);

            int totalRetrieved = 0;
            try {
//                ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
//                String blastdbIWGSC = extContext.getRealPath(BLAST_DB);
                String blastdbIWGSC = appData.getBLAST_DB();
                new TempFilesCleaner(appData.getBLAST_DIR(), appData.getBLAST_CLEANUP_DAYS()).run(); //DELETE BLAST FILES OLDER THAN n DAYS (default==7)
                perQueryResults = blastn.findHits(sequences, blastdbIWGSC);

                hitsForQueryDataModel = new HitsForQueryDataModel(perQueryResults.getResults());

                for (HitsForQuery qp : perQueryResults.getResults()) {
                    for (Hit p : qp.getHits()) {
                        totalRetrieved++;
                    }
                }
                //result available for one query only so goto the last tab and display
                if (perQueryResults.size() == 1) {
                    setSelectedQuery(hitsForQueryDataModel.getRow(0)); //calls setSeqSearchTabActive("2");
                } else if (perQueryResults.size() > 1) {
                    setSeqSearchTabActive("1");
                } else {
                    setSeqSearchTabActive("0");
                }

            } catch (Exception e) {
                StringBuilder s = new StringBuilder();
                s.append(generateEmailContent(submitTime));
                ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
                HttpServletRequest request = (HttpServletRequest) extContext.getRequest();
                s.append("\nUSER: ").append(request.getRemoteAddr()).append("\n");
                for (StackTraceElement ste : e.getStackTrace()) {
                    s.append("\n").append(ste.toString());
                }
                reusable.ExecProcessor.email(s.toString(), "POTAGE FATAL exception!", "radoslaw.suchecki@adelaide.edu.au", "no-reply@potage.local");
                growl(FacesMessage.SEVERITY_FATAL, "Fatal error!", "Alignment failed!", ":formSearch2:searchMessages2");
                setSeqSearchTabActive("0");
            }

            if (perQueryResults.isEmpty() || totalRetrieved == 0) {
//                //injecting param for js
//                addMessage("No promoter regions found!", "", "mainpanel", FacesMessage.SEVERITY_FATAL);
//                RequestContext.getCurrentInstance().getCallbackParams().put("showResults", false); //should not be necessary as is set as false at Submit
//                RequestContext.getCurrentInstance().getCallbackParams().put("showXML", true); //allows user to save blastn results 
//                if (!email.isEmpty()) {
//                    StringBuilder sb1 = new StringBuilder();
//                    reusable.ExecProcessor.email("No promoter regions were retrieved", "PromoterFinder notification: job failed.", email, "no-reply@hathor.acpfg.local");
//                }
                growl(FacesMessage.SEVERITY_WARN, "Bad luck!", "No matches found!", "searchMessages");
                setSeqSearchTabActive("0");

            } else {
                growl(FacesMessage.SEVERITY_INFO, "Hit(s) found!", "Alignment successful", ":formSearch2:searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Results", "should remain availabe for up to " + appData.getBLAST_CLEANUP_DAYS() + " days at " + blastn.getResultsLink("peru"), ":formSearch2:searchMessages2");

            }
        } else {
            growl(FacesMessage.SEVERITY_FATAL, "Error!", "No input sequeces!", "searchMessages");
            setSeqSearchTabActive("0");
        }
    }

    private void processRetrievedResults() {
        if (!perQueryResults.hasResults()) {
            if (perQueryResults.hasInput()) {
                if (perQueryResults.hasExitValue()) {
                    Integer exitValue = perQueryResults.getExitValue();
                    if (exitValue == 0) {
                        growl(FacesMessage.SEVERITY_WARN, "Alignment completed", "No hits found", "searchMessages");
                    } else {
                        growl(FacesMessage.SEVERITY_ERROR, "Alignment failed", "BLAST exit code: " + exitValue, "searchMessages");
                    }
                } else {
                    growl(FacesMessage.SEVERITY_INFO, "Results not available", "BLAST alignment may still be running...", "searchMessages");
                }
            } else {
                growl(FacesMessage.SEVERITY_WARN, "Not available: ", "Requested alignment results have not been found", "searchMessages");
            }
        } else {
            hitsForQueryDataModel = new HitsForQueryDataModel(perQueryResults.getResults());
            int totalRetrieved = 0;
            for (HitsForQuery qp : perQueryResults.getResults()) {
                for (Hit p : qp.getHits()) {
                    totalRetrieved++;
                }
            }
            //result available for one query only so goto the last tab and display
            if (perQueryResults.size() == 1) {
                setSelectedQuery(hitsForQueryDataModel.getRow(0)); //calls setSeqSearchTabActive("2");
            } else if (perQueryResults.size() > 1) {
                setSeqSearchTabActive("1");
            } else {
                setSeqSearchTabActive("0");
            }
            if (perQueryResults.isEmpty() || totalRetrieved == 0) {
                growl(FacesMessage.SEVERITY_WARN, "Alignment unsuccessful", "No matches found!", "searchMessages");
                setSeqSearchTabActive("0");

            } else {
                growl(FacesMessage.SEVERITY_INFO, "Alignment successful", "Hit(s) found!", ":formSearch2:searchMessages2");
            }
        }
    }

    public void loadExampleFasta(ActionEvent actionEvent) {
        try {
            ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
//            uploadedXmlFileName = extContext.getRealPath("//resources//example.blastn.xml");
//            File f = new File(uploadedXmlFileName);
//            uploadedXmlFileBaseName = f.getName();

            String fasta = InReader.readInputToString(extContext.getRealPath("//resources//example.fasta"));
            File f = new File(extContext.getRealPath("//resources//example.fasta"));
//            String fasta = InReader.readInputToString("/home/rad/NetBeansProjects/potage/web/resources/example.fasta");
//            File f = new File("/home/rad/NetBeansProjects/potage/web/resources/example.fasta");
            double size = reusable.CommonMaths.round((double) f.length() / 1024, 2);
            if (setFileContentStringValidate(fasta)) {
                growl(FacesMessage.SEVERITY_INFO, "File:", "Example FASTA file successfully uploaded.", "searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Size:", size + " kB", "searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Number of sequences:", "" + sequences.size(), "searchMessages2");
            }

//            addMessage("Example files loaded!", "", "intro", FacesMessage.SEVERITY_INFO);
//            addMessage("Example XML file loaded!", "", "advanced", FacesMessage.SEVERITY_INFO);
        } catch (Exception e) {
            e.printStackTrace();
            growl(FacesMessage.SEVERITY_ERROR, "Error!", "Example file upload failed!", "searchMessages2");
        }
    }

    public void loadExampleSequence(ActionEvent actionEvent) {
        try {
            ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
            String fasta = InReader.readInputToString(extContext.getRealPath("//resources//example.seq"));
            File f = new File(extContext.getRealPath("//resources//example.seq"));
            double size = reusable.CommonMaths.round((double) f.length() / 1024, 2);
            if (setFileContentStringValidate(fasta)) {
                growl(FacesMessage.SEVERITY_INFO, "File:", "Example sequence loaded.", "searchMessages2");
                growl(FacesMessage.SEVERITY_INFO, "Size:", size + " kB", "searchMessages2");
                growl(FacesMessage.SEVERITY_WARN, "FASTA ID", "FASTA identifier line added", "searchMessages2");
            }
        } catch (Exception e) {
            e.printStackTrace();
            growl(FacesMessage.SEVERITY_ERROR, "Error!", "Failed to load the example sequence!", "searchMessages2");
        }
    }

    private String generateEmailContent(Date submitTime) {
        StringBuilder sb = new StringBuilder("Results summary:\n");
        for (HitsForQuery prs : perQueryResults.getResults()) {
            sb.append(prs.getQueryId()).append("\t:\t").append(prs.getHits().size()).append(" hits identified.\n");
        }
        sb.append("\nJob submitted: ").append(submitTime).append("\n");;
        sb.append("\nJob completed: ").append(new Date()).append("\n");
        sb.append("\nContact radoslaw.suchecki@adelaide.edu.au with questions or comments about the POTAGE application. \n\n"
                + "ACPFG Bioinformatics Group "
                //                + "University of Adelaide, School of Agriculture, Food and Wine \n "
                //                + "Plant Genomics Centre, Waite Campus, SA, Australia. \n "
                + "\n");
        return sb.toString();
    }

    public boolean isAutoDisplayCharts() {
        return autoDisplayCharts;
    }

    public void setAutoDisplayCharts(boolean autoDisplayCharts) {
        this.autoDisplayCharts = autoDisplayCharts;
    }

//    public void chartItemSelect(ItemSelectEvent event) {
//        int i = event.getItemIndex() + 3;
//        if (fpkmTableHeaders != null && i >= 0 && i <= fpkmTableHeaders.length + 1) {
//            growl(FacesMessage.SEVERITY_INFO, "Sample selected", fpkmTableHeaders[i], "growl");
//        }
//    }
    public void poll() {
        final DataGrid d = (DataGrid) FacesContext.getCurrentInstance().getViewRoot().findComponent(":formCentre:chartsGrid");
        System.err.println(d.getEmptyMessage());
    }

    public boolean isAppendUnordered() {
        return appendUnordered;
    }

    public void setAppendUnordered(boolean appendUnordered) {
        this.appendUnordered = appendUnordered;
    }

    public Integer getIndexOfQuery(String key) {
        Integer idx = null;
        for (int i = 0; i < loadedGenes.size(); i++) {
            Gene current = loadedGenes.get(i);
            if (current.getGeneId().equalsIgnoreCase(key) || current.getContig().getId().equals(key)) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    public ArrayList<Gene> getLoadedGenes() {
        return loadedGenes;
    }

    public static void main(String[] args) {
        MainPopsBean mainPopsBean = new MainPopsBean();
        mainPopsBean.loadExampleFasta(null);
    }

    public AppDataBean getAppData() {
        return appData;
    }

    private boolean isChromosomeLabel(String label) {
        String genomes[] = {"A", "B", "D"};
        for (int i = 1; i < 8; i++) {
            for (String g : genomes) {
                if (label.equals(i + g)) {
                    return true;
                }
            }
        }
        return false;
    }
}
