package uk.ac.shef.dcs.jate.app;

import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.shef.dcs.jate.JATEException;
import uk.ac.shef.dcs.jate.JATEProperties;
import uk.ac.shef.dcs.jate.algorithm.RIDF;
import uk.ac.shef.dcs.jate.feature.FrequencyTermBased;
import uk.ac.shef.dcs.jate.feature.FrequencyTermBasedFBMaster;
import uk.ac.shef.dcs.jate.model.JATETerm;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppRIDF extends App {

	private final Logger log = LoggerFactory.getLogger(AppRIDF.class.getName());

	/**
	 * initialise RIDF by pre-filtering and post-filtering parameters
	 *
	 * @param initParams
	 *            accepted pre-filtering and post-filtering parameters
	 * @throws JATEException
	 * @see AppParams
	 */
	public AppRIDF(Map<String, String> initParams) throws JATEException {
		super(initParams);
	}

	/**
	 * @param args
	 *            command-line params accepting solr home path, solr core name
	 *            and more optional run-time parameters
	 * @see uk.ac.shef.dcs.jate.app.AppParams
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			printHelp();
			System.exit(1);
		}
		String solrHomePath = args[args.length - 2];
		String solrCoreName = args[args.length - 1];

		Map<String, String> params = getParams(args);
		String jatePropertyFile = getJATEProperties(params);
		String corpusDir = getCorpusDir(params);

		List<JATETerm> terms;
		try {
			App ridf = new AppRIDF(params);
			if (isCorpusProvided(corpusDir)) {
				ridf.index(Paths.get(corpusDir), Paths.get(solrHomePath), solrCoreName, jatePropertyFile);
			}
			terms = ridf.extract(solrHomePath, solrCoreName, jatePropertyFile);

			if (isExport(params)) {
				ridf.write(terms);
			}

			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JATEException e) {
			e.printStackTrace();
		}

	}

	@Override
	public List<JATETerm> extract(SolrCore core, String jatePropertyFile) throws IOException, JATEException {
		JATEProperties properties = getJateProperties(jatePropertyFile);

		return extract(core, properties);
	}

	public List<JATETerm> extract(SolrCore core, JATEProperties properties) throws JATEException {
		if (core.isClosed()) {
			core.open();
		}
		SolrIndexSearcher searcher = core.getSearcher().get();
//		try {
			this.freqFeatureBuilder = new FrequencyTermBasedFBMaster(searcher, properties, 0);
			this.freqFeature = (FrequencyTermBased) freqFeatureBuilder.build();

			RIDF attf = new RIDF();
			attf.registerFeature(FrequencyTermBased.class.getName(), this.freqFeature);

			List<String> candidates = new ArrayList<>(this.freqFeature.getMapTerm2TTF().keySet());

			filterByTTF(candidates);

			List<JATETerm> terms = attf.execute(candidates);
			terms = cutoff(terms);

			addAdditionalTermInfo(terms, searcher, properties.getSolrFieldNameJATENGramInfo(),
					properties.getSolrFieldNameID());
			return terms;
//		} finally {
//			try {
//				searcher.close();
//			} catch (IOException e) {
//				log.error(e.toString());
//			}
//		}
	}

}
