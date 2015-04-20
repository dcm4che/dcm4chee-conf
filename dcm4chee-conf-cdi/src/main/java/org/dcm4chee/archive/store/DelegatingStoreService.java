package org.dcm4chee.archive.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.conf.StoreAction;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.conf.cdi.dynamicdecorators.DelegatingService;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;


@DelegatingService
public class DelegatingStoreService implements StoreService {

    public StoreService getDelegate() {
        return delegate;
    }

    private StoreService delegate;

    public void setDelegate(StoreService delegate) {
        this.delegate = delegate;
    }

    public StoreSession createStoreSession(StoreService storeService) throws DicomServiceException {
        return delegate.createStoreSession(storeService);
    }

    public StoreContext createStoreContext(StoreSession session) {
        return delegate.createStoreContext(session);
    }

    public void initStorageSystem(StoreSession session) throws DicomServiceException {
        delegate.initStorageSystem(session);
    }

    public void initMetaDataStorageSystem(StoreSession session) throws DicomServiceException {
        delegate.initMetaDataStorageSystem(session);
    }

    public void initSpoolDirectory(StoreSession session) throws DicomServiceException {
        delegate.initSpoolDirectory(session);
    }

    public void writeSpoolFile(StoreContext session, Attributes fmi, Attributes attrs) throws DicomServiceException {
        delegate.writeSpoolFile(session, fmi, attrs);
    }

    public void writeSpoolFile(StoreContext context, Attributes fmi, InputStream data) throws DicomServiceException {
        delegate.writeSpoolFile(context, fmi, data);
    }

    public void parseSpoolFile(StoreContext context) throws DicomServiceException {
        delegate.parseSpoolFile(context);
    }

    public void onClose(StoreSession session) {
        delegate.onClose(session);
    }

    public void store(StoreContext context) throws DicomServiceException {
        delegate.store(context);
    }

    public Path spool(StoreSession session, InputStream in, String suffix) throws IOException {
        return delegate.spool(session, in, suffix);
    }

    public void coerceAttributes(StoreContext context) throws DicomServiceException {
        delegate.coerceAttributes(context);
    }

    public void processFile(StoreContext context) throws DicomServiceException {
        delegate.processFile(context);
    }

    public void updateDB(StoreContext context) throws DicomServiceException {
        delegate.updateDB(context);
    }

    public void updateDB(EntityManager em, StoreContext context) throws DicomServiceException {
        delegate.updateDB(em, context);
    }

    public Instance findOrCreateInstance(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.findOrCreateInstance(em, context);
    }

    public Series findOrCreateSeries(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.findOrCreateSeries(em, context);
    }

    public Study findOrCreateStudy(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.findOrCreateStudy(em, context);
    }

    public Patient findOrCreatePatient(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.findOrCreatePatient(em, context);
    }

    public StoreAction instanceExists(EntityManager em, StoreContext context, Instance instance) throws DicomServiceException {
        return delegate.instanceExists(em, context, instance);
    }

    public Instance createInstance(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.createInstance(em, context);
    }

    public Series createSeries(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.createSeries(em, context);
    }

    public Study createStudy(EntityManager em, StoreContext context) throws DicomServiceException {
        return delegate.createStudy(em, context);
    }

    public void updateInstance(EntityManager em, StoreContext context, Instance inst) throws DicomServiceException {
        delegate.updateInstance(em, context, inst);
    }

    public void updateSeries(EntityManager em, StoreContext context, Series series) throws DicomServiceException {
        delegate.updateSeries(em, context, series);
    }

    public void updateStudy(EntityManager em, StoreContext context, Study study) throws DicomServiceException {
        delegate.updateStudy(em, context, study);
    }

    public void updatePatient(EntityManager em, StoreContext context, Patient patient) {
        delegate.updatePatient(em, context, patient);
    }

    public void cleanup(StoreContext context) {
        delegate.cleanup(context);
    }

    public void fireStoreEvent(StoreContext context) {
        delegate.fireStoreEvent(context);
    }

    public int[] getStoreFilters() {
        return delegate.getStoreFilters();
    }

    public void storeMetaData(StoreContext context) throws DicomServiceException {
        delegate.storeMetaData(context);
    }

}
