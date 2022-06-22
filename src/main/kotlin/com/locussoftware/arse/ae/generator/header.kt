/**
 * Creates the top-level header methods of the message class
 */
fun createHeaderMethod(messageType: String, versionCode: String, sheetLines: List<String>, fields: List<String>) : String {
    return """     
@In
private EntityManager entityManager;

private boolean isLastEntity = false;
        
@In(create=true)
SmooksController smooksController;

@Logger
protected static Log log;
protected BatchResponse response = new BatchResponse();

protected boolean discharge = false;

protected CoparnType coparnType = null;
protected UserProfile userProfile = null;
protected MessageFunction messageFunction = null;
protected IntegrationMessageLog originalMessage = null;
protected EdiRecipientConnectionDetail ediCon = null;
protected MessagePlatform messagePlatform = null;
protected CustomDataMappingAction cdma = new CustomDataMappingAction();

protected Company company = null;

protected Voyage voyage = null;
protected Terminal terminal = null;
protected Company defaultBookingAgent = null;
protected Account defaultShipper = null;
protected List<ShippingInstruction> shippingInstructions = null;
protected ContainerReleaseReference containerReleaseReference = null;

protected int uniqueContainerOperatorCount = 0;

${overrideProcess(messageType)}
public ${getProcessMethodReturnType(messageType)} process(${defineParams(getHeaderParams(messageType))}) throws OdysseyBatchException {

    // Load in company, protective to ensure that a company always exists, even if not passed in through parameter
    if(company == null) {
        this.company = ediCon.getCompany();
    }
    
    this.company = company;

    // Set other class variables
    if(userProfile == null){
        userProfile = UserProfile.createSystemUserProfile(company, entityManager);
    }
    
    this.messageFunction = messageFunction;
    this.messagePlatform = ediCon.getMessagePlatform();
    this.ediCon = ediCon;
    
    this.cdma = new CustomDataMappingAction();
    cdma.setMappingPartyCode(this.ediCon.getMappingCode());
    
    if(!${createConditionWithChecks(getValueForElement(sheetLines, fields, "UNB").trim('[', ']').split("(?<=(&&|\\|\\|))|(?=(&&|\\|\\|))".toRegex()), fields)}) {
        return null;
    }

    try {
        ${headerMessageBodyCaller(messageType, versionCode, sheetLines)}
    } catch (OdysseyException ex) {
        log.error("Data Error encountered when processing ${messageType.toUpperCase()} : #0",
                ex.getMessage());
        log.error(Strings.EXCEPTION,ex);

        response.getErrors().add(
                new Error("EDI", MessageFormat.format(MessagesHelper
                        .getMessages().get("edi.iftmcs.errorProcessing"),
                        ex.getMessage())));
        throw new OdysseyBatchException(response);

    } catch (Exception e) {
        log.error("Error encountered when processing ${messageType.toUpperCase()} : #0",
                e.getMessage());
        log.error(Strings.EXCEPTION,e);

        response.getErrors().add(
                new Error("EDI", MessageFormat.format(MessagesHelper
                        .getMessages().get("odyssey.errors.processing"), e
                        .getMessage())));
        throw new OdysseyBatchException(response);
    }
}
    
protected IntegrationMessageLog createIntegrationMessageLog(@Nonnull EdiRecipientConnectionDetail ediDetail, @Nonnull Company company, ${defineParams(getImlParams(messageType))}) throws OdysseyException {

    UnLocode effectiveLoadPort = null;
    Company effectivePortAgent = null;
    
${integrationMessageLogCreator(messageType)}

    return IntegrationMessageLogDAO.getInstance(this.entityManager).create(
            MessageLogHelper.getInstance(this.entityManager).getGroupIdentifier(company),
            company.getCompanyId(),
            ediDetail.getSenderId(),
            ediDetail.getReceiverId(),
            IntegrationMessageType.${messageType.toUpperCase()},
            IntegrationMessageDirection.O,
            null,
            "$versionCode",
            "$messageType",
            IntegrationMessageStatus.PROCESSING,
            ediDetail,
            false,
            effectivePortAgent,
            effectiveLoadPort);
}

protected void add(UNEdifactInterchange41 interchange, UNEdifactMessage41 message){
    if(interchange.getMessages()==null){
        interchange.setMessages(new ArrayList<UNEdifactMessage41>());
    }
    interchange.getMessages().add(message);
}

protected org.milyn.smooks.edi.unedifact.model.r41.types.Party createParty(String str, String qual, String internalId){
  org.milyn.smooks.edi.unedifact.model.r41.types.Party p = new org.milyn.smooks.edi.unedifact.model.r41.types.Party();
  p.setId(str);
  p.setCodeQualifier(qual);
  p.setInternalId(internalId);
  return p;
}

protected String filterOutEmptySegments(@Nonnull String edi) {
    StringBuilder rebuiltMessage = new StringBuilder();

    String[] splitEdi = edi.split("'");
    char lastChar = ' ';

    for(String s : splitEdi) {
        // Check for escape character
        if(lastChar != '?') {
            // Only rebuild message if segment is not empty (***+)
            if(!(s.length() == 4 && s.charAt(3) == '+')) {
                rebuiltMessage.append(s).append("'");
            }
        } else{
            rebuiltMessage.append(s).append("'");
        }

        // Set the final character of the message, to check for escape char
        lastChar = s.charAt(s.length() - 1);
    }

    return rebuiltMessage.toString();
}

SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");

protected DateTime createDateTime(Date date){
    DateTime dt = new DateTime();

    dt.setDate(dateFormat.format(date));
    dt.setTime(timeFormat.format(date));
    return dt;
}

// The following non-class specific methods should be moved into a shared utility class

protected double getBliCommodityTotalGrossWeight(@Nonnull BookLineItem bookLineItem) {
    double totalGrossWeight = 0.0;
    
    for (CommodityBookLineItemList cblil : bookLineItem.getCommodityBookLineItemLists()) {
        Double grossWeight = cblil.getGrossWeight();
        
        if(grossWeight != null){
            totalGrossWeight += grossWeight;
        }
    }
    
    return totalGrossWeight;
}

protected String getBliCommodityTotalVolume(@Nonnull BookLineItem bookLineItem) {
    float totalVolume = 0.0f;
    
    for (CommodityBookLineItemList cblil : bookLineItem.getCommodityBookLineItemLists()) {
        Float volume = cblil.getVolume();
        
        if(volume != null){
            totalVolume += volume;
        }
    }
    
    return new DecimalFormat("#.##").format(totalVolume);
}

@CheckForNull
protected CommodityBookLineItemList getFirstDangerousCommodity(@Nonnull SiContainerAllocation siContainerAllocation) {

    for(CommodityBookLineItemList commodityBookLineItemList : siContainerAllocation.getBookLineItem().getCommodityBookLineItemLists()) {
        if(commodityBookLineItemList.getHazardousDetail() != null) {
            return commodityBookLineItemList;
        }
    }
    
    return null;
}

@CheckForNull
protected CommodityBookLineItemList getFirstDangerousCommodity(@Nonnull BookLineItem bookLineItem) {

for(CommodityBookLineItemList commodityBookLineItemList : bookLineItem.getCommodityBookLineItemLists()) {
  if(commodityBookLineItemList.getHazardousDetail() != null) {
    return commodityBookLineItemList;
  }
}

return null;
}

protected String getUnLocodeOrBlank(@Nonnull String city, @Nullable String country) {
  if(city.isEmpty()) {
    return "";
  }
  
  List<UnLocode> locodes = UnLocodeLookup.getInstance().findByCity(city);
  if(locodes.size() == 1) {
    return locodes.get(0).getLocodeNoSpaces();
  } else if(locodes.size() > 1 && country != null) {
    UnLocode locode = UnLocodeLookup.getInstance().find(city, country);

    if(locode != null) {
      return locode.getLocodeNoSpaces();
    }
  }

  return "";
}

protected int countOfDangerousCommoditiesInContainer(@Nonnull SiContainerAllocation siContainerAllocation) {
    int dgsCount = 0;
    
    for(CommodityBookLineItemList commodityBookLineItemList : siContainerAllocation.getBookLineItem().getCommodityBookLineItemLists()) {
        if(commodityBookLineItemList.getHazardousDetail() != null) {
            dgsCount++;
        }
    }
    
    return dgsCount;
}

protected int countContainersFromBookings(@Nonnull List<Booking> bookings) {
    int count = 0;

    for(Booking booking : bookings) {
        for(BookLineItem bookLineItem : booking.getBookLineItems()) {
            count += bookLineItem.getNoContainers();
        }
    }
    
    return count;
}

private ExternalBooking getPrevImport(@Nonnull Booking booking) {
    if(this.messagePlatform != null) {
        List<ExternalBooking> ex = booking.getImportedBookings(this.messagePlatform);
        if(ex != null && !ex.isEmpty()) {
            return ex.get(0);
        }
    }
    
    return null;
}

private String getPreviousImportReference(@Nonnull Booking booking) {
    ExternalBooking prevImport = getPrevImport(booking);
    
    if(prevImport == null) {
        return "";
    }
    
    return prevImport.getReference();
}

private boolean isPreviousImportStatusAccepted(@Nonnull Booking booking) {
    ExternalBooking prevImport = getPrevImport(booking);

    if(prevImport == null) {
        return false;
    }

    return prevImport.getStatus().equals(GenericStatus.CONFIRMED);
}

private boolean isPreviousImportStatusRejected(@Nonnull Booking booking) {
    return !isPreviousImportStatusAccepted(booking);
}

protected String getDeclineReasonForBooking(@Nonnull Booking booking) {
    
    String[] rejectReason = booking.getAddInfo().split("Decline: ");
  
    if(rejectReason.length > 1) {
        return rejectReason[1];
    } else{
        return "";
    }
}

protected int strPos(@Nullable String inputString, char searchChar) {
    if(StringUtils.isBlank(inputString)) {
        return 0;
    }
  
    int counter = 0;
  
    for(char c : inputString.toCharArray()) {
        if(c == searchChar) {
            return counter;
        }
        counter++;
    }
  
    return counter;
}

protected int strPos(@Nullable String inputString, @Nonnull String searchCharAsString) {
    if(StringUtils.isBlank(searchCharAsString)) {
        return 0;
    }

    return strPos(inputString, searchCharAsString.charAt(0));
}

@CheckForNull
protected String getTareWeightFromStock(@Nonnull ContainerReleaseReference crr, @Nullable SiContainerAllocation sica, @Nullable ContainerReleaseReferenceAllocation crra) { 

  if(crra == null && sica != null) {
    for (ContainerReleaseReferenceAllocation crras : crr.getContainerReleaseReferenceAllocations()) {
      if (crras.getContainerNumber().equals(sica.getContainerNumber())) {
        crra = crras;
      }
    }
  }
  
  if (crra != null) {
    // Lookup container and take tare weight from there
    try {
      ContainerStock cs = ContainerStockHelper.getInstance().getContainer(crr.getCompany(), crra.getContainerNumber());
      if (cs != null) {
        Double tareW = cs.getWeightTare();
        if (tareW != null) {
          return String.valueOf(cs.getWeightTare());
        }
      }
    } catch (OdysseyException e) {
      return null;
    }
  }

  return String.valueOf(crr.getContType().getDefaultTare());
}

protected Certificate getCertificate(@Nonnull String type, @Nonnull ShippingInstruction si) {
  for(Certificate certificate : si.getCertificates()) {
    if(certificate.getType().toString().equals(type)) {
      return certificate;
    }
  }
  
  return new Certificate();
}

@CheckForNull
protected SiContainerAllocation getFirstUnallocatedContainerOnShipInstrLineItem(@Nullable ShipInstrLineItem sili) {
  for(SiContainerAllocation sica : sili.getSiContainerAllocations()) {
    if(StringUtils.isBlank(sica.getContainerNumber())) {
      return sica;
    }
  }
  
  return null;
}

protected String getUnLocodeCDM(@Nonnull UnLocode unLocode) {
    return cdma.getSourceMappingOrDefault(UnLocode.class.getName(), unLocode.getEntityId(), unLocode.getLocodeNoSpaces(), this.company);
}

protected String getTerminalCDM(@Nonnull Terminal terminal) {
    return cdma.getSourceMappingOrDefault(Terminal.class.getName(), terminal.getEntityId(), terminal.getSmdgCode(), this.company);
}

protected String getContTypeCDM(@Nonnull ContType contType) {
  return cdma.getSourceMappingOrDefault(ContType.class.getName(), contType.getEntityId(), contType.getIsoCode00B(), this.company);
}

protected String getPackTypeCDM(@Nonnull PackageType packageType) {
  return cdma.getSourceMappingOrDefault(PackageType.class.getName(), packageType.getPackTypeId(), packageType.getPackageCode(), this.company);
}

protected String getVoyageCDM(@Nonnull Voyage voyage) {
  return cdma.getSourceMappingOrDefault(Voyage.class.getName(), voyage.getEntityId(), voyage.getVoyageNo(), this.company);
}

protected String getStringAbbrevCDM(@Nonnull String string) {
  return cdma.getSourceMappingOrDefault(CustomDataMappingAction.CustomKeyType.ABBREVIATION, string, "", this.company);
}

protected String getContainerEventTypeCDM(@Nonnull ContainerEventType eventType) {
    return cdma.getSourceMappingOrDefault(ContainerEventType.class.getName(), eventType, eventType.name(), this.company);
}

protected boolean isContainerEventTypeCDM(@Nonnull ContainerEventType eventType) {
    return !cdma.getSourceMappingOrDefault(ContainerEventType.class.getName(), eventType, "", this.company).isEmpty();
}

protected String getContCharCDM(@Nonnull String contChar) {
  return cdma.getSourceMappingOrDefault(ContainerCharacteristic.class.getName(), contChar, contChar, this.company);
}

protected boolean isContCharCDM(@Nonnull String contChar) {
return !cdma.getSourceMappingOrDefault(ContainerCharacteristic.class.getName(), contChar, "", this.company).isEmpty();
}

protected String throwError(@Nonnull String error) throws OdysseyException {
 throw new OdysseyException(error);
}

protected String throwError(@Nonnull String error, @Nonnull String p1) throws OdysseyException {
    throw new OdysseyException(error.replace("%1", p1));
}
protected String throwError(@Nonnull String error, @Nonnull String p1, @Nonnull String p2) throws OdysseyException {
    throw new OdysseyException(error.replace("%1", p1).replace("%2", p2));
}

protected SchedulePort getMostRecentPortOfEUImport(@Nonnull SchedulePort schedulePort) {
    for(SchedulePort euImport : EconomicZoneAction.getInstance().getPortsOfEUImport(schedulePort.getVoyage().getVisitedPorts())) {
        if(schedulePort.getScheduleDate().getEtdDate().after(euImport.getScheduleDate().getEtaDate())) {
            return euImport;
        }
    }
    
    return schedulePort;
}

@CheckForNull
protected String getSealNumber(int loopCounter, @Nonnull SiContainerAllocation sica) {
  if(sica.getSealNumber() == null) {
    return null;
  }
  
  String[] splitSeal = sica.getSealNumber().split(",");
  
  if(loopCounter >= splitSeal.length) {
    return null;
  } else{
    return splitSeal[loopCounter];
  }
}

protected String strSubsM(String input, int arrayPosition) {
  if(input.isEmpty() || input.split("%").length <= arrayPosition) {
    return "";
  }
  
  return input.split("%")[arrayPosition];
}

protected int getNumberOfUniqueContainerOperatorContactDetailsWithImportExport(@Nonnull List<ShippingInstruction> sis, @Nonnull SchedulePort schedulePort) {
 Set<String> vatNumbers = new HashSet<>();

 for(ShippingInstruction si : sis) {
  String vatNumber = getContainerOperatorContactDetail(si.getBooking(), schedulePort);

  if (StringUtils.isNotBlank(vatNumber)) {
   vatNumbers.add(vatNumber);
  }
 }

 return vatNumbers.size() + 1;
}

protected String getContainerOperatorContactDetail(@Nonnull Booking booking, @Nonnull SchedulePort schedulePort) {
  ContainerOperator containerOperator = booking.getContainerOperator();

  if(containerOperator != null) {
    AddressDetailList containerOperatorAdl = containerOperator.getAddressDetailList();

    if(containerOperatorAdl != null) {
      for (AddressDetailList adl : containerOperatorAdl.getAddressBook().getAddressDetailLists()) {
        ContactDetail cd = adl.getContactDetail();

        if(schedulePort.getUnLocode().equals(cd.getUnLocode())) {
          if ((cd.isImportContact() && this.discharge)
                  || (cd.isExportContact() && !this.discharge)) {
               return cd.getVatNumber();
             } else {
               return "";
          }
        }
      }
    }
  }

  return "";
}

protected void checkTerminal(@Nonnull List<ShippingInstruction> siList, @Nonnull SchedulePort schedulePort) throws OdysseyBatchException {
  this.terminal = null;
  for(ShippingInstruction si : siList) {
    Terminal t = si.getBooking().getMainBookingItinerary().getTerminal(schedulePort);
    if(terminal == null) {
      terminal = t;
    } else if(!terminal.equals(t)) {
      response.getErrors().add(new Error("EDI", "Mixture of terminals detected. Please only select one Terminal"));
      throw new OdysseyBatchException(response);
    }
  }
}

  /**
   * Aggregate the commodities from the SIs
   *
   * @param sis
   */
 protected void createAggregatedCommodities(@Nonnull List<ShippingInstruction> sis) {
 for(ShippingInstruction si : sis) {
 List<SiCommodity> siCommodities = new ArrayList<>();

   si.setSiCommodities(siCommodities);

 for(SiContainerAllocation sica : si.getSiContainerAllocations()) {
  for(SiContainerCommodityList siccl : sica.getSiContainerCommodityLists()) {
    boolean found = false;

  for(SiCommodity siCommodity : siCommodities) {
   if(siCommodity.getPackageType().equals(siccl.getCommodityBookLineItemList().getPackageType())
     && siccl.getHtsCode() != null
       && siCommodity.getHsCode() != null
     && siccl.getHtsCode().equals(siCommodity.getHsCode())
      && Integer.parseInt(siccl.getHtsCode()) > 100
       && Integer.parseInt(siCommodity.getHsCode()) > 100) {
   // Matching commodity found
   addToSiCommodity(siCommodity, siccl, sica);
   found = true;
   break;
   }
  }

  if(!found) {
    // No match found; Add new SiCommodity
    SiCommodity newCommodity = new SiCommodity(siccl.getCommodityBookLineItemList().getPackageType(),
            siccl.getHtsCode(),
            siccl.getSpainHS(),
            siccl.getCommodityBookLineItemList());
    addToSiCommodity(newCommodity, siccl, sica);

    siCommodities.add(newCommodity);
  }
  }
 }
 }
}

private void addToSiCommodity(@Nonnull SiCommodity siCommodity,
                              @Nonnull SiContainerCommodityList siccl,
                              @Nonnull SiContainerAllocation sica) {
  siCommodity.addNumberOfPackages(siccl.getNumberOfPackages());
  siCommodity.addGrossWeight(siccl.getTotalGrossWeight());
  siCommodity.addSiContainer(sica.getContainerNumber(), siccl.getNumberOfPackages());
}

protected String strSubs(@Nonnull String input, int from, int to) {
    if(input == null) {
     return "";
    }
     
  return input.substring(from, Math.min(input.length(), to));
}

protected UNB41 createHeaderUNB(@Nonnull IntegrationMessageLog integrationMessageLog) {
    UNB41 unb = new UNB41();
    SyntaxIdentifier si = new SyntaxIdentifier();
    si.setId(${getValueForElement(sheetLines, fields, "0001")});
    si.setVersionNum(${getValueForElement(sheetLines, fields, "0002")});
    unb.setSyntaxIdentifier(si);
    unb.setRecipient(createParty(${getValueForElement(sheetLines, fields, "0010")}, ${getValueForElement(sheetLines, fields, "0007")}, ${getValueForElement(sheetLines, fields, "0014")}));
    unb.setSender(createParty(${getValueForElement(sheetLines, fields, "0004")}, ${getValueForElement(sheetLines, fields, "0007")}, ${getValueForElement(sheetLines, fields, "0008")}));
    unb.setDate(createDateTime(new Date()));
    unb.setControlRef(${getValueForElement(sheetLines, fields, "0020")});
    unb.setApplicationRef(${getValueForElement(sheetLines, fields, "0026")});
    unb.setProcessingPriorityCode(${getValueForElement(sheetLines, fields, "0029")});
    return unb;
}

protected UNH41 createHeaderUNH(@Nonnull IntegrationMessageLog integrationMessageLog) {
    UNH41 unh = new UNH41();
    MessageIdentifier mi = new MessageIdentifier();
    unh.setMessageRefNum(${getValueForElement(sheetLines, fields, "0062")});
    mi.setId(${getValueForElement(sheetLines, fields, "0065")});
    mi.setVersionNum(${getValueForElement(sheetLines, fields, "0052")});
    mi.setReleaseNum(${getValueForElement(sheetLines, fields, "0054")});
    mi.setControllingAgencyCode(${getValueForElement(sheetLines, fields, "0051")});
    mi.setAssociationAssignedCode(${getValueForElement(sheetLines, fields, "0057")});
    unh.setMessageIdentifier(mi);
    return unh;
}

protected UNT41 createTrailerUNT(@Nonnull EDIWritable message, @Nonnull String groupId) throws IllegalArgumentException, IllegalAccessException {
    UNT41 unt = new UNT41();
    unt.setSegmentCount(SegmentCounter.getSegmentCount(message) + 2); // UNH + UNT
    unt.setMessageRefNum(groupId);
    return unt;
}

protected UNZ41 createTrailerUNZ(@Nonnull String groupId){
    UNZ41 unz = new UNZ41();
    unz.setControlCount(1);
    unz.setControlRef(groupId);
    return unz;
}

"""
}

/**
 * Returns a list of parameters, depending on the message type
 */
fun getHeaderParams(messageType: String) : List<String> {
    return when(messageType) {
        "Coparn" -> listOf("CoparnType coparnType", "List<ContainerReleaseReference> containerReleaseReferences", "Company company", "ContainerLocation containerLocation", "Voyage voyage", "SchedulePort schedulePort", "MessageFunction messageFunction")
        "Coprar" -> listOf("EntityManager entityManager", "Log log", "CustomDataMappingAction customDataMappingAction", "CoprarType coprarType", "List<Booking> bookings", "Company company", "Voyage voyage", "SchedulePort schedulePort", "Terminal terminal")
        "Coreor" -> listOf("List<ContainerReleaseReferenceAllocation> containerReleaseReferenceAllocations", "Company company", "ContainerLocation containerLocation", "Voyage voyage", "SchedulePort schedulePort")
        "Cuscar" -> listOf("EdiRecipientConnectionDetail ediCon", "List<SiContainerAllocation> siContainerAllocations", "Company company", "Voyage voyage", "SchedulePort schedulePort", "MessageFunction messageFunction", "boolean discharge")
        "Ifcsum" -> listOf("EdiRecipientConnectionDetail ediCon", "List<ShippingInstruction> shippingInstructions", "SchedulePort schedulePort", "MessageFunction messageFunction", "MessageDirection messageDirection", "SchedulePortEdiMap spEdi", "Map<String,Object> parameters")
        "Iftmcs" -> listOf("List<Booking> bookings")
        "Iftmbf", "Iftdgn", "Iftmbc" -> listOf("EdiRecipientConnectionDetail ediCon", "List<Booking> bookings", "MessageFunction messageFunction")
        "Iftsta" -> listOf("EdiRecipientConnectionDetail ediCon", "ContainerEvent containerEvent", "MessageFunction messageFunction")
        else -> listOf()
    }
}

fun getImlParams(messageType: String) : List<String> {
    return when(messageType) {
        "Coparn" -> listOf("Voyage voyage", "SchedulePort schedulePort", "MessageFunction messageFunction")
        "Coprar" -> listOf("List<Booking> bookings", "Voyage voyage", "SchedulePort schedulePort", "Terminal terminal")
        "Coreor" -> listOf("List<ContainerReleaseReferenceAllocation> containerReleaseReferenceAllocations", "ContainerLocation containerLocation", "Voyage voyage", "SchedulePort schedulePort")
        "Cuscar" -> listOf("Voyage voyage", "SchedulePort schedulePort")
        "Ifcsum" -> listOf("SchedulePort schedulePort", "SchedulePortEdiMap spEdi")
        "Iftmcs" -> listOf("Booking booking")
        "Iftmbf", "Iftdgn", "Iftmbc" -> listOf("EdiRecipientConnectionDetail ediCon", "Booking booking")
        "Iftsta" -> listOf("MessageFunction messageFunction")
        else -> listOf()
    }
}

fun getCreatorParams(messageType: String) : List<String> {
    return when(messageType) {
        "Coparn" -> listOf("ContainerReleaseReference containerReleaseReference", "Company company", "Voyage voyage", "SchedulePort schedulePort", "MessageFunction messageFunction")
        "Coprar" -> listOf("Company company", "List<Booking> bookings", "Voyage voyage", "SchedulePort schedulePort", "Terminal terminal")
        "Coreor" -> listOf("List<ContainerReleaseReferenceAllocation> containerReleaseReferenceAllocations", "ContainerLocation containerLocation", "Voyage voyage", "SchedulePort schedulePort")
        "Cuscar" -> listOf("List<SiContainerAllocation> siContainerAllocations", "Company company", "Voyage voyage", "SchedulePort schedulePort", "MessageFunction messageFunction", "boolean discharge")
        "Ifcsum" -> listOf("List<ShippingInstruction> shippingInstructions", "SchedulePort schedulePort", "MessageFunction messageFunction", "SchedulePortEdiMap spEdi")
        "Iftmcs" -> listOf("Booking booking")
        "Iftmbf", "Iftdgn", "Iftmbc" -> listOf("Booking booking", "MessageFunction messageFunction")
        "Iftsta" -> listOf("ContainerEvent containerEvent", "MessageFunction messageFunction")
        else -> listOf()
    }
}

fun defineParams(params: List<String>) : String {
    return "@Nonnull " + params.joinToString(", @Nonnull ")
}

fun passParams(params: List<String>) : String {
    return params.joinToString(", ") { it.split(" ")[1] }
}

fun getProcessMethodReturnType(messageType: String) : String {
    return when(messageType) {
        "Iftmbf", "Iftmcs", "Iftdgn", "Iftmbc" -> "List<IntegrationMessageLog>"
        else -> "IntegrationMessageLog"
    }
}

fun headerMessageBodyCaller(messageType: String, versionCode: String, sheetLines: List<String>) : String {
    return when(messageType) {
        "Coparn" -> """
        
        IntegrationMessageLog log = null;
        
        // We only need the first one, as the list only ever has one value (it's a list because of legacy code)
        ContainerReleaseReference containerReleaseReference = containerReleaseReferences.get(0);
        this.coparnType = coparnType;
        
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        log = messageLog;
        
        return log;
        """
        "Coprar" -> """
        this.terminal = terminal;
      this.messageFunction = MessageFunction.ORIGINAL;
            
        if(coprarType.equals(CoprarType.DISCHARGE)) {
            this.discharge = true;
        }
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        return messageLog;
        """
        "Coreor" -> """
        this.containerReleaseReference = (containerReleaseReferenceAllocations.size() > 0) ? containerReleaseReferenceAllocations.get(0).getContainerReleaseReference() : new ContainerReleaseReference();
        
        IntegrationMessageLog log = null;
        this.messageFunction = MessageFunction.ORIGINAL;
        
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        log = messageLog;
        
        return log;
        """
        "Cuscar" -> """
        IntegrationMessageLog log = null;
        this.discharge = discharge;
        
      this.shippingInstructions = new ArrayList<>();
      
    for(SiContainerAllocation sica : siContainerAllocations) {
      ShipInstrLineItem sili = sica.getShipInstrLineItem();
      
      if(sili != null && !this.shippingInstructions.contains(sili.getShippingInstruction())) {
        this.shippingInstructions.add(sili.getShippingInstruction());
      }
    }
        
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        log = messageLog;
        
        return log;
        """
        "Ifcsum" -> """    
        this.discharge = GenerateIftmcs.MessageDirection.IMPORT.equals(messageDirection);
        this.voyage = schedulePort.getScheduleDate().getVoyage();
        this.shippingInstructions = shippingInstructions;
        this.schedulePort = schedulePort;
        this.createAggregatedCommodities(shippingInstructions);
        this.uniqueContainerOperatorCount = this.getNumberOfUniqueContainerOperatorContactDetailsWithImportExport(shippingInstructions, schedulePort);
        if(shippingInstructions.get(0).getBooking().getBookingAgent() != null) {
            this.defaultBookingAgent = shippingInstructions.get(0).getBooking().getBookingAgent();
        }
        this.bookingItinerary = shippingInstructions.get(0).getBooking().getMainBookingItinerary();
        if(shippingInstructions.get(0).getShipper() != null) {
            this.defaultShipper = shippingInstructions.get(0).getShipper();
        }
        checkTerminal(shippingInstructions, schedulePort);
            
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        return messageLog;
        """
        "Iftmbf", "Iftmcs" -> """
        List<IntegrationMessageLog> logs = new ArrayList<>();
        
        for (Booking booking : bookings) {
            
            bookingItinerary = booking.getMainBookingItinerary();
        
            ${headerMessageCreator(messageType, versionCode, sheetLines)}
            
            logs.add(messageLog);
        }
        
        return logs;
        """
        "Iftsta" -> """
          Booking eventBook = containerEvent.getBooking();
          ContainerEventType eventType = containerEvent.getEventType();
    
          if(containerEvent.getVoyage() != null) {
              // Get directly from event voyage if populated
              voyage = containerEvent.getVoyage();
          } else if(eventBook != null) {
              // Find the correct voyage to check, based on the event type and location
              List<Voyage> eventVoyages = eventBook.getVoyageList();
    
              if(!eventVoyages.isEmpty()) {
                  switch (eventType) {
                      case DEPOTRELE:
                          // Get the trade route of the first voyage
                          voyage = eventBook.getVoyageList().get(0);
                          break;
                      case DEPOTRETN:
                          // Get the trade route of the last voyage
                          voyage = eventVoyages.get(eventVoyages.size() - 1);
                          break;
                      case TERMARRIVE:
                      case TERMDEPART:
                          // Check all voyages for a matching event location, to determine the correct voyage for OLAF
                          for(Voyage v : eventVoyages) {
                              SchedulePort load = eventBook.getMainBookingItinerary().getLoadPort(v);
                              SchedulePort discharge = eventBook.getMainBookingItinerary().getDischargePort(v);
                              UnLocode eventLoc = containerEvent.getUnLocodeFromEventOrLocation();
    
                              if((load != null && load.getUnLocode().equals(eventLoc))
                                      || (discharge != null && discharge.getUnLocode().equals(eventLoc))) {
                                  voyage = v;
                              }
                          }
                          break;
                  }
              }
          }
            
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        return messageLog;
        """
        else -> """
        ${headerMessageCreator(messageType, versionCode, sheetLines)}
        
        return messageLog;
        """
    }
}

fun integrationMessageLogCreator(messageType: String): String {
    return when(messageType) {
        "Iftmbf", "Iftmcs" -> """
        // Effective Load Port
        for(VoyageLegMetaData vlmd : booking.getVoyageLegs()) {
            if(vlmd.getLeg().getOriginalVoyage().isSelected()) {
                effectiveLoadPort = vlmd.getLeg().getLoadPort().getUnLocode();
                break;
            }
        }
        
        // Effective Port Agent
		if(effectiveLoadPort != null && booking != null) {
			CompanyAssociation ca = AgentAction.getInstance().getPortAgent(booking.getCompany(), effectiveLoadPort);
			if(ca != null) {
				effectivePortAgent = ca.getCompanyByAgentId();
			}
		}
        """
        "Coprar" -> """ 
		if(terminal != null) {
			effectivePortAgent = terminal.getReportingAgent();
			effectiveLoadPort = terminal.getUnLocode();
		}
        """
        "Coreor" -> """   
		if(containerLocation != null ) {
			effectivePortAgent = containerLocation.getReportingAgent();
			effectiveLoadPort = containerLocation.getUnLocode();
		}
        """
        "Cuscar" -> """
        if(schedulePort != null) {
            CompanyAssociation ca = AgentAction.getInstance().getPortAgent(company, schedulePort.getUnLocode());
            if(ca != null){
                effectivePortAgent = ca.getCompanyByAgentId();
            }
            effectiveLoadPort = schedulePort.getUnLocode();
        }
        
		Company reportingAgent = null;
		UnLocode loc = null;
		String desc = desc= String.format("Cuscar for Voyage %s to Partner %s",voyage.getVoyageNumber(), ediCon.getConnectionName());
		if(schedulePort!=null){
			CompanyAssociation ca = AgentAction.getInstance().getPortAgent(company, port.getUnLocode());
			if(ca!=null){
				reportingAgent = ca.getCompanyByAgentId();
			}
			loc = schedulePort.getUnLocode();
			desc= String.format("Cuscar on Voyage %s for Port %s to Partner %s",port.getOutboundVoyageNumber() ,schedulePort.getUnLocode(), ediCon.getConnectionName());
		}
        """
        else -> """
        """
    }
}

fun headerMessageCreator(messageType: String, versionCode: String, sheetLines: List<String>) : String {
    return """
            IntegrationMessageLog messageLog = createIntegrationMessageLog(ediCon, company, ${passParams(getImlParams(messageType))});
        
            UNEdifactInterchange41 interchange = new UNEdifactInterchange41();
            interchange.setInterchangeHeader(createHeaderUNB(messageLog));
            interchange.setInterchangeTrailer(createTrailerUNZ(messageLog.getGroupId()));
            
            UNEdifactMessage41 message = new UNEdifactMessage41();
            message.setMessageHeader(createHeaderUNH(messageLog));
            
            $messageType ${messageType.toLowerCase()} = null;
            
            try {
                ${messageType.toLowerCase()} = create${messageType}(messageLog, ${passParams(getCreatorParams(messageType))});
            } catch(NullPointerException e) {
                // Throw a useful null pointer error, which would likely be the most expected error
                StackTraceElement pointOfError = e.getStackTrace()[0];
                StatusMessages.instance().addFromResourceBundle(StatusMessage.Severity.ERROR, "Error: Null value in " + pointOfError.getFileName() + " on line " + pointOfError.getLineNumber());
            } catch(Exception e) {
                throw e;
            }
            
            message.setMessage(${messageType.toLowerCase()});
            
            message.setMessageTrailer(createTrailerUNT(${messageType.toLowerCase()}, messageLog.getGroupId()));
            add(interchange, message);
            
            StringWriter outEDI = new StringWriter();
            ${versionCode}InterchangeFactory ${versionCode.toLowerCase()}factory = smooksController.get${versionCode}InterchangeFactory();
            ${versionCode.toLowerCase()}factory.toUNEdifact(interchange, outEDI);
        
            String out = outEDI.toString();
            if(ediCon.getTerminateWithLinefeed()==null || ediCon.getTerminateWithLinefeed()){
                out = out.replaceAll("(?<!\\?)'([^'\n\r])", "'\n${'$'}1");
            }
            out = filterOutEmptySegments(out);
            
            if(ediCon.getCharset().equals(Charset.UNOA)) {
                out = out.toUpperCase();
            }
            
            IntegrationMessageStore ims = new IntegrationMessageStore(ediCon.getCharset());
            ims.setIntegrationMessageLog(messageLog);
            ims.setMessageStr(out);
            
            messageLog.setIntegrationMessageStore(ims);
            
            entityManager.persist(messageLog);
            entityManager.persist(ims);
    """
}

fun createClassDeclaration(messageType: String, versionCode: String, identifier: String) : String {
    val className = "${messageType}${versionCode}${identifier}"

    return when (messageType) {
        "Coparn" -> """
@Name("process$className")
public class Process$className implements IProcessCoparn, Serializable {
    
private static final long serialVersionUID = 1L;
        """
        "Coprar" -> """
@Name("process$className")
public class Process$className extends AbstractCoprarHandler {
        """
        "Coreor" -> """
@Name("process$className")
public class Process$className implements IProcessCoreor {
        """
        "Cuscar" -> """
@Name("process$className")
public class Process$className extends ProcessCuscar {
        """
        "Ifcsum" -> """
@Name("process$className")
public class Process$className extends AbstractManifestGenerator implements AbstractIfcsumHandler {
        """
        "Iftmbf", "Iftdgn", "Iftmbc" -> """
@Name("process$className")
public class Process$className extends BookingInterface {
        """
        "Iftsta" -> """
@Name("process$className")
public class Process$className implements Serializable, IftstaProcessor {
        """
        else -> """
@Name("process$className")
public class Process$className {
        """
    }
}

fun createInterfaceMethods(messageType: String) : String {
    return when (messageType) {
        "Coparn" -> """
@Override
public List<MessageFunction> getSupportedMessageFunctions() {
  return Arrays.asList(MessageFunction.REPLACE,MessageFunction.CANCELATION,MessageFunction.ORIGINAL);
}

@Override
public List<IntegrationMessageLog> cancelMessage(ContainerReleaseReference newEntity, DirtyObject changes, IntegrationMessageLog oldLog) throws OdysseyException, OdysseyBatchException {
  this.setOriginalMessage(oldLog);
  determinOriginalMessage(oldLog);
  ArrayList<IntegrationMessageLog> logs = new ArrayList<IntegrationMessageLog>();
  if(this.originalCoparn!=null){
    ContainerReleaseReferenceEdiInfo ediInfo = new ContainerReleaseReferenceEdiInfo(newEntity,originalCoparn.getCoparnType());
    //the actual terminal/depot might have changed. Make sure we send it to the correct one
    ContainerLocation containerLocation = null;
    List<ContainerLocation> containerLocs = oldLog.getEdiRecipientConnectionDetail().getContainerLocations();
    if(containerLocs.size()==1){
      containerLocation = containerLocs.get(0);
    }else{
      Idable oldDepot = null;
      Idable oldTerminal = null;
      if(CoparnType.GATE_OUT.equals(originalCoparn.getCoparnType())){
        oldDepot = (Idable)changes.getOldValue("turnOutContainerDepot");
        oldTerminal = (Idable)changes.getOldValue("turnOutTerminal");
        //default to current
        containerLocation = newEntity.getTurnOutContainerLocation();
      }else{
        oldDepot = (Idable)changes.getOldValue("turnInContainerDepot");
        oldTerminal = (Idable)changes.getOldValue("turnInTerminal");
        //default to current
        containerLocation = newEntity.getTurnInContainerLocation();
      }
      if(oldDepot!=null){
        containerLocation = entityManager.find(ContainerDepot.class, oldDepot.getId());
      }else if(oldTerminal!=null){
        containerLocation = entityManager.find(Terminal.class, oldTerminal.getId());
      }
    }
    if(containerLocation!=null){
      ediInfo.setContainerLocation(containerLocation);
    }

    if(this.getSupportedMessageFunctions().contains(MessageFunction.CANCELATION)){
      this.messageFunction = (MessageFunction.CANCELATION);
      IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(), messageFunction);
      mlog.setChildMessageType(IntegrationChildMessageType.CANCELATION);
      oldLog.getChildMessages().add(mlog);
      mlog.setParentMessage(oldLog);
      logs.add(mlog);
    }else if(this.getSupportedMessageFunctions().contains(MessageFunction.DELETION)){
      this.messageFunction = (MessageFunction.DELETION);
      List<String> oldContainers = originalCoparn.getContainers();
      int oldUnalloc = originalCoparn.getContainerQuantity()-originalCoparn.getAllocatedQuantity();
      this.addDeleteContainers = oldContainers;
      this.addDeleteQuantity = oldUnalloc;
      IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(), messageFunction);
      mlog.setChildMessageType(IntegrationChildMessageType.DELETION);
      oldLog.getChildMessages().add(mlog);
      mlog.setParentMessage(oldLog);
      logs.add(mlog);

    }else{
      throw new OdysseyException("Partner Connection does not support cancellation");
    }

  }
  return logs;
}

  public void setOriginalMessage(IntegrationMessageLog originalMessage) {
    this.originalMessage = originalMessage;
  }

  private CoparnInfo originalCoparn;

  private void determinOriginalMessage(IntegrationMessageLog oldLog){
    if(oldLog!=null && oldLog.getIntegrationMessageStore()!=null && StringUtils.isNotBlank(oldLog.getIntegrationMessageStore().getMessageStr())){
      originalCoparn = new CoparnInfo(oldLog);
    }
  }

  protected List<String> addDeleteContainers;

  protected int addDeleteQuantity  = 0;

  public String determineVoyageNumber(Voyage voyage, SchedulePort port) throws OdysseyException {
    return voyage.getEffectiveVoyageNumber(port,ediCon.getVoyageDirectionIndicatorHandling(), discharge);
  }

@Override
public List<IntegrationMessageLog> updateMessage(ContainerReleaseReference newEntity, DirtyObject changes, IntegrationMessageLog oldLog) throws OdysseyException, OdysseyBatchException {
		this.setOriginalMessage(oldLog);
		determinOriginalMessage(oldLog);
		ArrayList<IntegrationMessageLog> logs = new ArrayList<IntegrationMessageLog>();
		if(this.originalCoparn!=null){

			ContainerReleaseReferenceEdiInfo ediInfo = new ContainerReleaseReferenceEdiInfo(newEntity,originalCoparn.getCoparnType());
			log.info("ContainerLocation is managed #0", entityManager.contains(ediInfo.getContainerLocation()));
			if(this.getSupportedMessageFunctions().contains(MessageFunction.REPLACE)){
				//easy one just send replace message
				this.messageFunction = MessageFunction.REPLACE;
				IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(),this.messageFunction);
				mlog.setChildMessageType(IntegrationChildMessageType.REPLACE);
				oldLog.getChildMessages().add(mlog);
				mlog.setParentMessage(oldLog);
				logs.add(mlog);
			}else if(this.getSupportedMessageFunctions().contains(MessageFunction.ADDITION)
					&& this.getSupportedMessageFunctions().contains(MessageFunction.DELETION)
							&& this.getSupportedMessageFunctions().contains(MessageFunction.CHANGE)){
				List<String> oldContainers = originalCoparn.getContainers();
				List<String> newContainers = newEntity.getAllocatedContainerNumbers();
				log.info("Old allocated #0 #1", oldContainers.size(), CollectionUtil.toString(oldContainers, ", "));
				log.info("new allocated #0 #1", newContainers.size(),CollectionUtil.toString(newContainers, ", "));

				List<String> additions = new ArrayList<String>(newContainers);
				additions.removeAll(oldContainers);
				List<String> deletions = new ArrayList<String>(oldContainers);
				deletions.removeAll(newContainers);
				int newUnalloc = newEntity.getQuantity()-newEntity.getAllocatedQuantity();
				int oldUnalloc = originalCoparn.getContainerQuantity()-originalCoparn.getAllocatedQuantity();

				int unallocAdd = newUnalloc>oldUnalloc?newUnalloc-oldUnalloc:0;
				int unallocDel = oldUnalloc>newUnalloc?oldUnalloc-newUnalloc:0;

				log.info("Old COPARN total #0, allocated #1 unallocated #2", originalCoparn.getContainerQuantity(),originalCoparn.getAllocatedQuantity(),originalCoparn.getContainerQuantity()-originalCoparn.getAllocatedQuantity());
				log.info("New COPARN total #0, allocated #1 unallocated #2", newEntity.getQuantity(),newEntity.getAllocatedQuantity(),newEntity.getQuantity()-newEntity.getAllocatedQuantity());
				log.info("Allocated Additions #0, allocated deletions #1", additions.size(),deletions.size());
				this.addDeleteContainers = null;
				this.addDeleteQuantity = 0;
				//Best is to first add containers and then remove others. E.g. the terminal has already released 10 containers and we received the container numbers through the CODECOs
				//now if we would send a delete for 10 unalloc containers first and then a add for 10 alloc containers the first message might fail at the terminal as they have already released the 10.
				if((additions.size()+unallocAdd)>0){
					log.info("We need to add #0 containers #1 allocated and #2 unallocated",additions.size()+unallocAdd,additions.size(),unallocAdd);
                  this.messageFunction = (MessageFunction.ADDITION);
					this.addDeleteContainers = additions;
					this.addDeleteQuantity = unallocAdd;
					IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(),this.messageFunction);
					mlog.setChildMessageType(IntegrationChildMessageType.ADDITION);
					oldLog.getChildMessages().add(mlog);
					mlog.setParentMessage(oldLog);
					logs.add(mlog);
				}
				if((deletions.size()+unallocDel)>0){
					log.info("We need to delete #0 containers #1 allocated and #2 unallocated",deletions.size()+unallocDel,deletions.size(),unallocDel);
                  this.messageFunction = (MessageFunction.DELETION);
					this.addDeleteContainers = deletions;
					this.addDeleteQuantity = unallocDel;
					IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(),this.messageFunction);
					mlog.setChildMessageType(IntegrationChildMessageType.DELETION);
					oldLog.getChildMessages().add(mlog);
					mlog.setParentMessage(oldLog);
					logs.add(mlog);
				}
				//now check whether we have any other changes
				if(this.originalCoparn.hasChanges(newEntity, ediInfo.getVoyage(),this.determineVoyageNumber(ediInfo.getVoyage(),ediInfo.getSchedulePort()),this.cdma.getSourceMappingOrDefault(ContType.class.getName(), Long.valueOf(newEntity.getContType().getContTypeId()), newEntity.getContType().getIsoCode95B(), newEntity.getCompany()))){
					log.info("Sending change message");
					this.messageFunction = (MessageFunction.CHANGE);
					this.addDeleteContainers =null;
					this.addDeleteQuantity = 0;
					IntegrationMessageLog mlog = this.process(originalCoparn.getCoparnType(), Arrays.asList(newEntity), newEntity.getCompany(), ediInfo.getContainerLocation(), ediInfo.getVoyage(), ediInfo.getSchedulePort(),this.messageFunction);
					mlog.setChildMessageType(IntegrationChildMessageType.CHANGE);
					oldLog.getChildMessages().add(mlog);
					mlog.setParentMessage(oldLog);
					logs.add(mlog);
				}

			}
		}
		return logs;
}

@Override
public EntityManager getEntityManager() {
    return null;
}

@Override
public void setEntityManager(EntityManager entityManager) {

}

@Override
public void setCustomDataMappingAction(CustomDataMappingAction customDataMappingAction) {

}

@Override
public Log getLog() {
    return null;
}

@Override
public void setLog(Log log) {

}

@Override
public void setEdiRecipientDetail(EdiRecipientConnectionDetail ediCon) {
    this.ediCon = ediCon;
}
        """
        "Cuscar" -> """       
  @Override
  public String getVersion() {
    return null;
  }

  @Override
  public IntegrationMessageLog process(EdiRecipientConnectionDetail ediCon, List<SiContainerAllocation> sicas, Company company, Voyage voyage, SchedulePort schedulePort, boolean discharge) throws OdysseyBatchException {
    return this.process(ediCon, sicas, company, voyage, schedulePort, MessageFunction.ORIGINAL, discharge);
  }
        """
        "Coreor" -> """
            

  @Override
  public EntityManager getEntityManager() {
    return this.entityManager;
  }

  @Override
  public void setEntityManager(EntityManager entityManager) {
  this.entityManager = entityManager;
  }

  @Override
  public void setCustomDataMappingAction(CustomDataMappingAction customDataMappingAction) {
  this.cdma = customDataMappingAction;
  }

  @Override
  public Log getLog() {
    return null;
  }

  @Override
  public void setLog(Log log) {

  }

  @Override
  public void setEdiRecipientDetail(EdiRecipientConnectionDetail ediCon) {
  this.ediCon = ediCon;
  }
        """
        "Ifcsum" -> """ 
@Override
protected String getEntityInfo() {
    return null;
}

@Override
protected MessageFunction getMessageFunction() {
    return null;
}

@Override
protected String getCarrierManifestId(SchedulePort sp) {
    return null;
}
        """
        "Iftmbf", "Iftdgn", "Iftmbc" -> """
@Override
public List<IntegrationMessageLog> exportBookings(@Nonnull EdiRecipientConnectionDetail ediCon, @Nonnull List<Booking> bookings, @Nonnull MessageFunction messageFunction) throws OdysseyBatchException{
    List<IntegrationMessageLog> messageLog = this.process(ediCon, bookings, messageFunction);
    return messageLog;
}
        """
        "Iftsta" -> """ 
    @Override
    public void setAlternateStatus(String alternateStatus) {
        
    }

    @Override
    public void setAlternateIftstaType(AlternateIftstaType alternateIftstaType) {

    }
        """
        "Coprar" -> """
            

    public void setEdiRecipientDetail(EdiRecipientConnectionDetail ediCon){
        this.ediCon = ediCon;
    }
        """
        else -> ""
    }
}

fun overrideProcess(messageType: String) : String {
    return when (messageType) {
        "Iftmbf", "Iftdgn", "Iftmbc", "Ifcsum", "Cuscar" -> ""
        else -> "@Override"
    }
}