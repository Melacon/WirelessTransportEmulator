/*
 * utils.c
 *
 *  Created on: Aug 12, 2016
 *      Author: compila
 */

#include "utils.h"
#include <pthread.h>

/*
 * UTILS
 */
static const char *filename =  "/usr/src/OpenYuma/microwave-model-status.xml";
//static const char *filename =  "/home/emulator/workspace/OpenYuma/microwave-model-status.xml";

static status_t
    get_object_string (const val_value_t *elem,
                       const obj_template_t *stopobj,
                       xmlChar  *buff,
                       uint32 bufflen,
                       boolean normalmode,
                       ncx_module_t *mod,
                       uint32 *retlen,
                       boolean withmodname,
                       boolean forcexpath,
                       val_value_t *last_key);




static xmlDocPtr get_xml_doc_ptr();


xmlDocPtr xmlDefaultValues = NULL;

void update_status_values()
{
	while (TRUE)
	{
		pthread_mutex_lock(&lock);

		xmlFreeDoc(xmlDefaultValues);
		xmlDefaultValues = xmlParseFile(filename);
		YUMA_ASSERT(xmlDefaultValues == NULL, pthread_mutex_unlock(&lock); return, "Could not load XML file from path=%s. Aborting the update_status_values()", filename);

		pthread_mutex_unlock(&lock);

		sleep(5);
	}
}

static xmlDocPtr get_xml_doc_ptr()
{
	if (xmlDefaultValues != NULL)
	{
		return xmlDefaultValues;
	}

	xmlInitParser();

	xmlDefaultValues = xmlParseFile(filename);
	YUMA_ASSERT(xmlDefaultValues == NULL, return NULL, "Could not load XML file from path=%s", filename);

	YUMA_ASSERT(TRUE, NOP, "get_xml_doc_ptr was called successfully");
	return xmlDefaultValues;
}

status_t set_value_for_xpath(const xmlChar* xPathExpression, const xmlChar* new_value)
{
	pthread_mutex_lock(&lock);
	xmlDocPtr doc = get_xml_doc_ptr();
	xmlChar appended_xPathExpression[2048];

	sprintf(appended_xPathExpression, "/status%s", xPathExpression);

	xmlXPathContextPtr xpathCtx;
	xmlXPathObjectPtr xpathObj;

	xpathCtx = xmlXPathNewContext(doc);
	YUMA_ASSERT(xpathCtx == NULL, pthread_mutex_unlock(&lock); return ERR_INTERNAL_VAL, "xmlXPathNewContext failed!");

	xpathObj = xmlXPathEvalExpression(appended_xPathExpression, xpathCtx);
	YUMA_ASSERT(xpathObj == NULL, pthread_mutex_unlock(&lock); return ERR_INTERNAL_VAL, "xmlXPathEvalExpression failed!");

	int size = (xpathObj->nodesetval) ? xpathObj->nodesetval->nodeNr : 0;

	YUMA_ASSERT(TRUE, NOP, "Setting new_value=%s in xPath=%s", new_value, xPathExpression);

	for (int i = size - 1; i >= 0; --i)
	{
		YUMA_ASSERT(xpathObj->nodesetval->nodeTab[i] == NULL, continue, "NULL object received!");

		xmlNodeSetContent(xpathObj->nodesetval->nodeTab[i], new_value);

		if (xpathObj->nodesetval->nodeTab[i]->type != XML_NAMESPACE_DECL)
		{
			xpathObj->nodesetval->nodeTab[i] = NULL;
		}
	}

    xmlXPathFreeObject(xpathObj);
    xmlXPathFreeContext(xpathCtx);

    FILE *file = fopen(filename, "w");

    xmlDocDump(file, doc);

    fclose(file);

	pthread_mutex_unlock(&lock);

	return NO_ERR;
}

char* get_value_from_xpath(const xmlChar* xPathExpression)
{
	pthread_mutex_lock(&lock);
	xmlDocPtr doc = get_xml_doc_ptr();
	char* resultString = NULL;
	xmlChar appended_xPathExpression[2048];

	sprintf(appended_xPathExpression, "/status%s", xPathExpression);

	xmlXPathContextPtr xpathCtx;
	xmlXPathObjectPtr xpathObj;

	xpathCtx = xmlXPathNewContext(doc);
	YUMA_ASSERT(xpathCtx == NULL, return NULL, "xmlXPathNewContext failed!");

	xpathObj = xmlXPathEvalExpression(appended_xPathExpression, xpathCtx);
	YUMA_ASSERT(xpathObj == NULL, return NULL, "xmlXPathEvalExpression failed!");

	YUMA_ASSERT(TRUE, NOP, "Getting value from path=%s", appended_xPathExpression);

	int size = (xpathObj->nodesetval) ? xpathObj->nodesetval->nodeNr : 0;

	for (int i = 0; i<size; ++i)
	{
		int length = strlen(xmlNodeGetContent(xpathObj->nodesetval->nodeTab[i]));
		resultString = strndup(xmlNodeGetContent(xpathObj->nodesetval->nodeTab[i]), length);
		YUMA_ASSERT(TRUE, NOP, "Got back xmlString=%s having length=%d",
				xmlNodeGetContent(xpathObj->nodesetval->nodeTab[i]), length);
	}

    xmlXPathFreeObject(xpathObj);
    xmlXPathFreeContext(xpathCtx);
    pthread_mutex_unlock(&lock);

    return resultString;
}

status_t get_list_from_xpath(const xmlChar* xPathExpression, char **list_elements, int *num_of_elements)
{
	pthread_mutex_lock(&lock);
	xmlDocPtr doc = get_xml_doc_ptr();
    xmlChar appended_xPathExpression[2048];

    sprintf(appended_xPathExpression, "/status%s", xPathExpression);

	xmlXPathContextPtr xpathCtx;
	xmlXPathObjectPtr xpathObj;

	xpathCtx = xmlXPathNewContext(doc);
	YUMA_ASSERT(xpathCtx == NULL, return ERR_INTERNAL_VAL, "xmlXPathNewContext failed!");

	xpathObj = xmlXPathEvalExpression(appended_xPathExpression, xpathCtx);
	YUMA_ASSERT(xpathObj == NULL, return ERR_INTERNAL_VAL, "xmlXPathEvalExpression failed!");

	YUMA_ASSERT(TRUE, NOP, "Getting value from path=%s", appended_xPathExpression);

	*num_of_elements = (xpathObj->nodesetval) ? xpathObj->nodesetval->nodeNr : 0;

	for (int i = 0; i<*num_of_elements; ++i)
	{
		list_elements[i] = strdup(xmlNodeGetContent(xpathObj->nodesetval->nodeTab[i]));
		YUMA_ASSERT(list_elements[i] == NULL, return ERR_INTERNAL_MEM, "Could not allocate memory!");

		YUMA_ASSERT(TRUE, NOP, "Got back xmlString=%s", xmlNodeGetContent(xpathObj->nodesetval->nodeTab[i]));
	}

    xmlXPathFreeObject(xpathObj);
    xmlXPathFreeContext(xpathCtx);
    pthread_mutex_unlock(&lock);

    return NO_ERR;
}


void print_path_for_element(val_value_t *elem)
{
    obj_template_t *curr_obj = elem->obj;

    do
    {
        if (!obj_is_root(curr_obj))
        {
            if (curr_obj->objtype == OBJ_TYP_LIST)
            {
                val_value_t *lastkey = NULL;
                const xmlChar *k_MW_AirInterface_Pac_layerProtocol = VAL_STRING(agt_get_key_value(elem, &lastkey));

                log_debug("\n elem_name=%s[%s=\"%s\"]\n", obj_get_name(curr_obj), obj_get_keystr(curr_obj),
                        k_MW_AirInterface_Pac_layerProtocol);
            }
            else
            {
                log_debug("\n elem_name=%s\n", obj_get_name(curr_obj));
            }
        }

        curr_obj = obj_get_parent(curr_obj);
    }
    while (curr_obj != NULL);
}

//status_t get_xpath_string(const obj_template_t *obj,
//        xmlChar  *buff,
//        uint32 bufflen,
//        ncx_module_t *mod,
//        uint32 *retlen)
//{
//    obj_template_t obj = elem->obj;
//    *retlen = 0;
//    boolean topnode = FALSE;
//
//    if (obj->parent &&
//        !obj_is_root(obj->parent)) {
//        status_t res = get_object_string(obj->parent, buff, bufflen, retlen);
//        if (res != NO_ERR) {
//            return res;
//        }
//    } else {
//        topnode = TRUE;
//    }
//
//    if (!obj_has_name(obj)) {
//        /* should not enounter a uses or augment!! */
//        return NO_ERR;
//    }
//
//    if ((obj->objtype == OBJ_TYP_CHOICE ||
//                       obj->objtype == OBJ_TYP_CASE)) {
//        return NO_ERR;
//    }
//
//    /* get the name and check the added length */
//    const xmlChar *name = obj_get_name(obj);
//    uint32 namelen = xml_strlen(name), seplen = 1;
//
//    if (topnode) {
//        seplen = 0;
//    }
//
//    if (bufflen &&
//        (((*retlen) + namelen + + seplen + 1) > bufflen)) {
//        return ERR_BUFF_OVFL;
//    }
//
//    /* copy the name string recusively, letting the stack
//     * keep track of the next child node to write
//     */
//    if (buff) {
//        /* node separator char */
//        if (topnode) {
//            ;
//        } else {
//            buff[*retlen] = '/';
//        }
//
//        xml_strcpy(&buff[*retlen + seplen], name);
//    }
//
//    *retlen += (namelen + seplen);
//
//    return NO_ERR;
//
//}

static status_t
    get_object_string (const val_value_t *elem,
                       const obj_template_t *stopobj,
                       xmlChar  *buff,
                       uint32 bufflen,
                       boolean normalmode,
                       ncx_module_t *mod,
                       uint32 *retlen,
                       boolean withmodname,
                       boolean forcexpath,
                       val_value_t *last_key)
{
    *retlen = 0;

    boolean addmodname = withmodname || forcexpath;
    boolean topnode = FALSE, isList = FALSE;

    if (elem->obj->parent &&
        elem->obj->parent != stopobj &&
        !obj_is_root(elem->obj->parent)) {
        status_t res = get_object_string(elem->parent, stopobj, buff, bufflen,
                                         normalmode, mod, retlen,
                                         withmodname, forcexpath, last_key);
        if (res != NO_ERR) {
            return res;
        }
    } else {
        topnode = TRUE;
    }

    if (!obj_has_name(elem->obj)) {
        /* should not enounter a uses or augment!! */
        return NO_ERR;
    }

    if (forcexpath && (elem->obj->objtype == OBJ_TYP_CHOICE ||
                       elem->obj->objtype == OBJ_TYP_CASE)) {
        return NO_ERR;
    }

    const xmlChar *modname = NULL;
    uint32 modnamelen = 0;

    if (forcexpath) {
        modname = xmlns_get_ns_prefix(obj_get_nsid(elem->obj));
        if (!modname) {
            return SET_ERROR(ERR_INTERNAL_VAL);
        }
        modnamelen = xml_strlen(modname);
    } else {
        modname = obj_get_mod_name(elem->obj);
        modnamelen = xml_strlen(modname);
    }

    if (!addmodname && mod != NULL &&
        (xml_strcmp(modname, ncx_get_modname(mod)))) {
        addmodname = TRUE;
    }

    /* get the name and check the added length */
    const xmlChar *name = obj_get_name(elem->obj);
    uint32 namelen = xml_strlen(name), seplen = 1;

    const xmlChar *listkey = NULL;
    uint32 listkeylen = 0, list_key_val_len = 0;
    const xmlChar *list_key_value = NULL;

    if (elem->obj->objtype == OBJ_TYP_LIST)
    {
        isList = TRUE;
        list_key_value = VAL_STRING(agt_get_key_value(elem, &last_key));
        listkey = obj_get_keystr(elem->obj);
        listkeylen = xml_strlen(listkey);
        list_key_val_len = xml_strlen(list_key_value);
    }

    if (topnode && stopobj) {
        seplen = 0;
    }

    if (bufflen &&
        (((*retlen) + namelen + (addmodname?modnamelen:0) + (isList?(5 + listkeylen + list_key_val_len):0) + seplen + 1) > bufflen)) {
        return ERR_BUFF_OVFL;
    }

    /* copy the name string recusively, letting the stack
     * keep track of the next child node to write
     */
    if (buff) {
        /* node separator char */
        if (topnode && stopobj) {
            ;
        } else if (normalmode) {
            buff[*retlen] = '/';
        } else {
            buff[*retlen] = '.';
        }

        if (addmodname) {
            xml_strcpy(&buff[*retlen + seplen], modname);
            buff[*retlen + modnamelen + seplen] =
                (forcexpath || withmodname) ? ':' : '_';
            xml_strcpy(&buff[*retlen + modnamelen + seplen + 1], name);
        } else {
            xml_strcpy(&buff[*retlen + seplen], name);
        }

        if (isList && topnode)
        {
            xml_strcpy(&buff[*retlen + seplen + namelen], "[");
            xml_strcpy(&buff[*retlen + seplen + namelen + 1], listkey);
            xml_strcpy(&buff[*retlen + seplen + namelen + 1 + listkeylen], "=\"");
            xml_strcpy(&buff[*retlen + seplen + namelen + 1 + listkeylen + 2], list_key_value);
            xml_strcpy(&buff[*retlen + seplen + namelen + 1 + listkeylen + 2 + list_key_val_len], "\"]");
        }
    }

    if (addmodname) {
        *retlen += (namelen + modnamelen + seplen + 1);
    } else {
        *retlen += (namelen + seplen + (isList?(5+listkeylen+list_key_val_len):0));
    }
    return NO_ERR;

}  /* get_object_string */


status_t get_xpath_string_for_element(val_value_t *elem, xmlChar **buff)
{
    uint32    len;
    status_t  res;
    xmlChar *local_buff = NULL;

    /* figure out the length of the object ID */
    res = get_object_string(elem, NULL, NULL, 0, TRUE, NULL, &len,
                            FALSE, FALSE, NULL);
    if (res != NO_ERR) {
        return res;
    }

    local_buff = (xmlChar *)m__getMem(len+1);
    if (!local_buff) {
        return ERR_INTERNAL_MEM;
    }

    /* get the object ID for real this time */
    res = get_object_string(elem, NULL, local_buff, len+1, TRUE, NULL, &len,
                            FALSE, FALSE, NULL);
    if (res != NO_ERR) {
        m__free(local_buff);
        local_buff = NULL;
        *buff = NULL;
        return SET_ERROR(res);
    }

    *buff = (xmlChar *)m__getMem(len+1 + strlen("/status"));
    if (!*buff) {
        return ERR_INTERNAL_MEM;
    }

    sprintf(*buff, "/status%s", local_buff);
    m__free(local_buff);

    log_debug("\nxPath: %s\n", *buff);


    return NO_ERR;
}
