package com.ciandt.techgallery.service.impl;

import com.google.api.server.spi.response.BadRequestException;
import com.google.api.server.spi.response.InternalServerErrorException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.users.User;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;

import com.ciandt.techgallery.persistence.dao.SkillDAO;
import com.ciandt.techgallery.persistence.dao.impl.SkillDAOImpl;
import com.ciandt.techgallery.persistence.model.Skill;
import com.ciandt.techgallery.persistence.model.TechGalleryUser;
import com.ciandt.techgallery.persistence.model.Technology;
import com.ciandt.techgallery.service.SkillService;
import com.ciandt.techgallery.service.TechnologyService;
import com.ciandt.techgallery.service.UserServiceTG;
import com.ciandt.techgallery.service.enums.ValidationMessageEnums;
import com.ciandt.techgallery.service.model.ImportUserSkillTO;
import com.ciandt.techgallery.utils.TechGalleryUtil;
import com.ciandt.techgallery.utils.i18n.I18n;

import java.util.Date;
import java.util.logging.Logger;

/**
 * Services for Skill Endpoint requests.
 *
 * @author Felipe Goncalves de Castro
 *
 */
public class SkillServiceImpl implements SkillService {

  /*
   * Constants --------------------------------------------
   */
  private static final Logger log = Logger.getLogger(SkillServiceImpl.class.getName());
  private static final I18n i18n = I18n.getInstance();

  /*
   * Attributes --------------------------------------------
   */
  private static SkillServiceImpl instance;
  SkillDAO skillDao = SkillDAOImpl.getInstance();

  /** Technology service. */
  TechnologyService techService = TechnologyServiceImpl.getInstance();
  /** tech gallery user service. */
  UserServiceTG userService = UserServiceTGImpl.getInstance();

  /*
   * Constructors --------------------------------------------
   */
  private SkillServiceImpl() {}

  /**
   * Singleton method for the service.
   *
   * @author <a href="mailto:joaom@ciandt.com"> João Felipe de Medeiros Moreira </a>
   * @since 07/10/2015
   *
   * @return SkillServiceImpl instance.
   */
  public static SkillServiceImpl getInstance() {
    if (instance == null) {
      instance = new SkillServiceImpl();
    }
    return instance;
  }

  /*
   * Methods --------------------------------------------
   */
  @Override
  public Skill addOrUpdateSkill(Skill skill, User user)
      throws InternalServerErrorException, BadRequestException, NotFoundException {

    log.info("Starting creating or updating skill");

    validateInputs(skill, user);

    Technology technology = skill.getTechnology().get();
    TechGalleryUser techUser = userService.getUserByGoogleId(user.getUserId());
    Skill skillEntity = skillDao.findByUserAndTechnology(techUser, technology);

    // if there is a skillEntity, it is needed to inactivate it and create a new
    // one
    if (skillEntity != null) {
      log.info("Inactivating skill: " + skillEntity.getId());
      skillEntity.setInactivatedDate(new Date());
      skillEntity.setActive(Boolean.FALSE);
      skillDao.update(skillEntity);
    }

    final Skill newSkill = addNewSkill(skill, techUser, technology);

    return newSkill;
  }

  /**
   * Validate inputs of SkillResponse.
   *
   * @param skill inputs to be validate
   * @param user info about user from google
   *
   * @throws BadRequestException for the validations.
   * @throws InternalServerErrorException in case something goes wrong
   * @throws NotFoundException in case the information are not founded
   */
  private void validateInputs(Skill skill, User user)
      throws BadRequestException, NotFoundException, InternalServerErrorException {

    log.info("Validating inputs of skill");

    if (user == null || user.getUserId() == null || user.getUserId().isEmpty()) {
      throw new BadRequestException(ValidationMessageEnums.USER_GOOGLE_ENDPOINT_NULL.message());
    }

    final TechGalleryUser techUser = userService.getUserByGoogleId(user.getUserId());
    if (techUser == null) {
      throw new BadRequestException(ValidationMessageEnums.USER_NOT_EXIST.message());
    }

    if (skill == null) {
      throw new BadRequestException(ValidationMessageEnums.SKILL_CANNOT_BLANK.message());
    }

    if (skill.getValue() == null || skill.getValue() < 0 || skill.getValue() > 5) {
      throw new BadRequestException(ValidationMessageEnums.SKILL_RANGE.message());
    }

    if (skill.getTechnology() == null) {
      throw new BadRequestException(ValidationMessageEnums.TECHNOLOGY_ID_CANNOT_BLANK.message());
    }

  }

  private Skill addNewSkill(Skill skill, TechGalleryUser techUser, Technology technology) {
    log.info("Adding new skill...");

    final Skill newSkill = new Skill();
    newSkill.setTechGalleryUser(Ref.create(techUser));
    newSkill.setTechnology(Ref.create(technology));
    newSkill.setValue(skill.getValue());
    newSkill.setActive(Boolean.TRUE);
    final Key<Skill> newSkillKey = skillDao.add(newSkill);
    newSkill.setId(newSkillKey.getId());

    log.info("New skill added: " + newSkill.getId());

    return newSkill;
  }

  @Override
  public Skill getUserSkill(String techId, User user) throws BadRequestException,
      OAuthRequestException, NotFoundException, InternalServerErrorException {
    // user google id
    String googleId;
    // user from techgallery datastore
    TechGalleryUser tgUser;
    // User from endpoint can't be null
    if (user == null) {
      throw new OAuthRequestException(i18n.t("OAuth error, null user reference!"));
    } else {
      googleId = user.getUserId();
    }

    // TechGalleryUser can't be null and must exists on datastore
    if (googleId == null || googleId.equals("")) {
      throw new BadRequestException(i18n.t("Current user was not found!"));
    } else {
      // get the TechGalleryUser from datastore or PEOPLE API
      tgUser = userService.getUserByGoogleId(googleId);
      if (tgUser == null) {
        throw new BadRequestException(i18n.t("Endorser user do not exists on datastore!"));
      }
    }

    // Technology can't be null
    final Technology technology = techService.getTechnologyById(techId);
    if (technology == null) {
      throw new BadRequestException(i18n.t("Technology do not exists!"));
    }
    final Skill userSkill = skillDao.findByUserAndTechnology(tgUser, technology);
    if (userSkill == null) {
      throw new NotFoundException(i18n.t("User skill do not exist!"));
    } else {
      return userSkill;
    }
  }

  @Override
  public Skill getUserSkill(String techId, TechGalleryUser user) throws BadRequestException,
      OAuthRequestException, NotFoundException, InternalServerErrorException {
    // User can't be null
    if (user == null) {
      throw new OAuthRequestException(i18n.t("Null user reference!"));
    }

    // Technology can't be null
    final Technology technology = techService.getTechnologyById(techId);
    if (technology == null) {
      throw new NotFoundException(i18n.t("Technology do not exists!"));
    }
    final Skill userSkill = skillDao.findByUserAndTechnology(user, technology);
    if (userSkill == null) {
      return null;
    } else {
      return userSkill;
    }
  }

  @Override
  public String importUserSkill(ImportUserSkillTO[] userSkills, User user)
      throws NotFoundException, InternalServerErrorException, BadRequestException {
    for (ImportUserSkillTO userSkill : userSkills) {
      String email = userSkill.getEmail();
      TechGalleryUser techGalleryUser = userService.getUserSyncedWithProvider(email.split("@")[0]);
      for (String techSkill : userSkill.getTechSkill()) {
        String[] split = techSkill.split(";");
        Technology technology = recoverTechnologyById(split[0]);

        Skill skill = new Skill();
        skill.setActive(true);
        skill.setTechGalleryUser(Ref.create(techGalleryUser));
        skill.setTechnology(Ref.create(technology));
        skill.setValue(Integer.parseInt(split[1]));

        addOrUpdateSkill(skill, user);
      }
    }

    return null;
  }

  private Technology recoverTechnologyById(String techCompleteName) throws NotFoundException {
    String techName =
        techCompleteName.substring(techCompleteName.indexOf('['), techCompleteName.indexOf(']'));
    techName = TechGalleryUtil.slugify(techName);
    return techService.getTechnologyById(techName);
  }

}
