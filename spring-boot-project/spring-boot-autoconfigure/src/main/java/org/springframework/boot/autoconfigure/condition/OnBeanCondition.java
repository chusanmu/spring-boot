/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.condition;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} that checks for the presence or absence of specific beans.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Jakub Kubrynski
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @see ConditionalOnBean
 * @see ConditionalOnMissingBean
 * @see ConditionalOnSingleCandidate
 */
@Order(Ordered.LOWEST_PRECEDENCE)
class OnBeanCondition extends FilteringSpringBootCondition implements ConfigurationCondition {

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// TODO: 构建结果集数组
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		for (int i = 0; i < outcomes.length; i++) {
			// TODO: 把指定位置的配置文件拿到
			String autoConfigurationClass = autoConfigurationClasses[i];
			// TODO: 不等于空 去处理 去拿到 ConditionalOnBean 属性
			if (autoConfigurationClass != null) {
				Set<String> onBeanTypes = autoConfigurationMetadata.getSet(autoConfigurationClass, "ConditionalOnBean");
				outcomes[i] = getOutcome(onBeanTypes, ConditionalOnBean.class);
				// TODO: 如果返回的等于空，则表示符合条件，去做进一步的判断
				if (outcomes[i] == null) {
					// TODO: 把ConditionalOnSingleCandidate属性的值拿到
					Set<String> onSingleCandidateTypes = autoConfigurationMetadata.getSet(autoConfigurationClass,
							"ConditionalOnSingleCandidate");
					// TODO: 如果按照程序走的话，这里理应返回的也是null
					outcomes[i] = getOutcome(onSingleCandidateTypes, ConditionalOnSingleCandidate.class);
				}
			}
		}
		return outcomes;
	}

	/**
	 * TODO: 这里去匹配，其实和 annotation并没有多大的关系，它只做了一件事，记录日志信息
	 * @param requiredBeanTypes
	 * @param annotation
	 * @return
	 */
	private ConditionOutcome getOutcome(Set<String> requiredBeanTypes, Class<? extends Annotation> annotation) {
		// TODO: 如果缺少当前的requiredBeanTypes,那么肯定是不符合条件的
		List<String> missing = filter(requiredBeanTypes, ClassNameFilter.MISSING, getBeanClassLoader());
		// TODO: 如果missing不为空，那么肯定存在缺失的，那么肯定就不符合条件
		if (!missing.isEmpty()) {
			ConditionMessage message = ConditionMessage.forCondition(annotation)
					.didNotFind("required type", "required types").items(Style.QUOTE, missing);
			// TODO: 然后把不符合条件返回回去
			return ConditionOutcome.noMatch(message);
		}
		return null;
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ConditionMessage matchMessage = ConditionMessage.empty();
		// TODO: 把它的所有的注解拿到
		MergedAnnotations annotations = metadata.getAnnotations();
		// TODO: 如果注解上存在了 @ConditionalOnBean注解
		if (annotations.isPresent(ConditionalOnBean.class)) {
			// TODO: 去创建了一个元信息
			Spec<ConditionalOnBean> spec = new Spec<>(context, metadata, annotations, ConditionalOnBean.class);
			// TODO: 去 拿到匹配结果
			MatchResult matchResult = getMatchingBeans(context, spec);
			// TODO: 如果有不匹配的，则返回不匹配
			if (!matchResult.isAllMatched()) {
				// TODO: 最后 返回不匹配结果
				String reason = createOnBeanNoMatchReason(matchResult);
				return ConditionOutcome.noMatch(spec.message().because(reason));
			}
			// TODO: 否则构造匹配了的结果
			matchMessage = spec.message(matchMessage).found("bean", "beans").items(Style.QUOTE,
					matchResult.getNamesOfAllMatches());
		}
		// TODO: 如果注解上存在 ConditionalOnSingleCandidate 这个注解
		if (metadata.isAnnotated(ConditionalOnSingleCandidate.class.getName())) {
			Spec<ConditionalOnSingleCandidate> spec = new SingleCandidateSpec(context, metadata, annotations);
			// TODO: 获取匹配结果，看看是否匹配
			MatchResult matchResult = getMatchingBeans(context, spec);
			// TODO: 如果不匹配，构建不匹配信息，这里如果返回了true,表示在容器里面找到了这个bean
			if (!matchResult.isAllMatched()) {
				return ConditionOutcome.noMatch(spec.message().didNotFind("any beans").atAll());
			}
			// TODO: 如果在容器中找到了这个bean, 会走这个条件，接着去判断，判断这个bean是否是唯一的，或者存在 @Primary注解的 也是可以的
			else if (!hasSingleAutowireCandidate(context.getBeanFactory(), matchResult.getNamesOfAllMatches(),
					spec.getStrategy() == SearchStrategy.ALL)) {
				// TODO: 构建不匹配结果
				return ConditionOutcome.noMatch(spec.message().didNotFind("a primary bean from beans")
						.items(Style.QUOTE, matchResult.getNamesOfAllMatches()));
			}
			// TODO: 构建匹配成功结果
			matchMessage = spec.message(matchMessage).found("a primary bean from beans").items(Style.QUOTE,
					matchResult.getNamesOfAllMatches());
		}
		// TODO: 如果注解中存在 ConditionalOnMissingBean 注解
		if (metadata.isAnnotated(ConditionalOnMissingBean.class.getName())) {
			Spec<ConditionalOnMissingBean> spec = new Spec<>(context, metadata, annotations,
					ConditionalOnMissingBean.class);
			// TODO: 去获取匹配结果，如果annotation type, name有一个匹配上了，则返回noMatch,因为它是 ConditionOnMissingBean, 容器中没有，才返回true
			MatchResult matchResult = getMatchingBeans(context, spec);
			// TODO: 判断有没有匹配
			if (matchResult.isAnyMatched()) {
				// TODO: 如果有任意一个匹配上了，则构建不匹配 noMatch
				String reason = createOnMissingBeanNoMatchReason(matchResult);
				return ConditionOutcome.noMatch(spec.message().because(reason));
			}
			// TODO: 构建匹配消息
			matchMessage = spec.message(matchMessage).didNotFind("any beans").atAll();
		}
		// TODO: 构建匹配消息..., 到这就匹配成功返回了
		return ConditionOutcome.match(matchMessage);
	}

	protected final MatchResult getMatchingBeans(ConditionContext context, Spec<?> spec) {
		// TODO: 得到classLoader
		ClassLoader classLoader = context.getClassLoader();
		// TODO: 拿到beanFactory
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		// TODO: 如果搜索策略不是当前容器
		boolean considerHierarchy = spec.getStrategy() != SearchStrategy.CURRENT;
		// TODO: 会同时拿到泛型
		Set<Class<?>> parameterizedContainers = spec.getParameterizedContainers();
		// TODO: 如果搜索策略不是当前容器，那就去父容器立马找
		if (spec.getStrategy() == SearchStrategy.ANCESTORS) {
			BeanFactory parent = beanFactory.getParentBeanFactory();
			Assert.isInstanceOf(ConfigurableListableBeanFactory.class, parent,
					"Unable to use SearchStrategy.ANCESTORS");
			// TODO: 更改为父容器
			beanFactory = (ConfigurableListableBeanFactory) parent;
		}
		// TODO: 创建MatchResult
		MatchResult result = new MatchResult();
		// TODO: 这地方就是重点了，根据type找bean的name---> 得到忽略类型的beanName, 根据type找name
		Set<String> beansIgnoredByType = getNamesOfBeansIgnoredByType(classLoader, beanFactory, considerHierarchy,
				spec.getIgnoredTypes(), parameterizedContainers);
		// TODO: 把对应的bean拿到
		for (String type : spec.getTypes()) {
			// TODO: 根据type把bean的名称拿到
			Collection<String> typeMatches = getBeanNamesForType(classLoader, considerHierarchy, beanFactory, type,
					parameterizedContainers);
			Iterator<String> iterator = typeMatches.iterator();
			while (iterator.hasNext()) {
				String match = iterator.next();
				// TODO: 如果是忽略的，或者是scopedTarget,则移除
				if (beansIgnoredByType.contains(match) || ScopedProxyUtils.isScopedTarget(match)) {
					iterator.remove();
				}
			}
			// TODO: 如果当前bean没找到，那就往未匹配记录里面加一条
			if (typeMatches.isEmpty()) {
				result.recordUnmatchedType(type);
			}
			else {
				// TODO: 否则往匹配里面加一条
				result.recordMatchedType(type, typeMatches);
			}
		}
		// TODO: 下面就是根据注解和名字去做匹配
		for (String annotation : spec.getAnnotations()) {
			// TODO: 根据注解，去拿beanName
			Set<String> annotationMatches = getBeanNamesForAnnotation(classLoader, beanFactory, annotation,
					considerHierarchy);
			// TODO: 然后把忽略的移除掉
			annotationMatches.removeAll(beansIgnoredByType);
			// TODO: 如果最后是空，就是一个都没找到，把它记录下来
			if (annotationMatches.isEmpty()) {
				result.recordUnmatchedAnnotation(annotation);
			}
			else {
				// TODO: 否则 记录下找到了的
				result.recordMatchedAnnotation(annotation, annotationMatches);
			}
		}
		// TODO: 根据name去匹配
		for (String beanName : spec.getNames()) {
			// TODO: 忽略的bean里面不包含这个beanName,并且 容器里面是包含的
			if (!beansIgnoredByType.contains(beanName) && containsBean(beanFactory, beanName, considerHierarchy)) {
				// TODO: 那么就是匹配的，否则是不匹配的
				result.recordMatchedName(beanName);
			}
			else {
				// TODO: 记录下不匹配的bean
				result.recordUnmatchedName(beanName);
			}
		}
		// TODO: 最后把结果返回回去
		return result;
	}

	private Set<String> getNamesOfBeansIgnoredByType(ClassLoader classLoader, ListableBeanFactory beanFactory,
			boolean considerHierarchy, Set<String> ignoredTypes, Set<Class<?>> parameterizedContainers) {
		Set<String> result = null;
		for (String ignoredType : ignoredTypes) {
			Collection<String> ignoredNames = getBeanNamesForType(classLoader, considerHierarchy, beanFactory,
					ignoredType, parameterizedContainers);
			result = addAll(result, ignoredNames);
		}
		return (result != null) ? result : Collections.emptySet();
	}

	private Set<String> getBeanNamesForType(ClassLoader classLoader, boolean considerHierarchy,
			ListableBeanFactory beanFactory, String type, Set<Class<?>> parameterizedContainers) throws LinkageError {
		try {
			return getBeanNamesForType(beanFactory, considerHierarchy, resolve(type, classLoader),
					parameterizedContainers);
		}
		catch (ClassNotFoundException | NoClassDefFoundError ex) {
			return Collections.emptySet();
		}
	}

	private Set<String> getBeanNamesForType(ListableBeanFactory beanFactory, boolean considerHierarchy, Class<?> type,
			Set<Class<?>> parameterizedContainers) {
		Set<String> result = collectBeanNamesForType(beanFactory, considerHierarchy, type, parameterizedContainers,
				null);
		return (result != null) ? result : Collections.emptySet();
	}

	private Set<String> collectBeanNamesForType(ListableBeanFactory beanFactory, boolean considerHierarchy,
			Class<?> type, Set<Class<?>> parameterizedContainers, Set<String> result) {
		result = addAll(result, beanFactory.getBeanNamesForType(type, true, false));
		for (Class<?> container : parameterizedContainers) {
			ResolvableType generic = ResolvableType.forClassWithGenerics(container, type);
			result = addAll(result, beanFactory.getBeanNamesForType(generic, true, false));
		}
		if (considerHierarchy && beanFactory instanceof HierarchicalBeanFactory) {
			BeanFactory parent = ((HierarchicalBeanFactory) beanFactory).getParentBeanFactory();
			if (parent instanceof ListableBeanFactory) {
				result = collectBeanNamesForType((ListableBeanFactory) parent, considerHierarchy, type,
						parameterizedContainers, result);
			}
		}
		return result;
	}

	private Set<String> getBeanNamesForAnnotation(ClassLoader classLoader, ConfigurableListableBeanFactory beanFactory,
			String type, boolean considerHierarchy) throws LinkageError {
		Set<String> result = null;
		try {
			result = collectBeanNamesForAnnotation(beanFactory, resolveAnnotationType(classLoader, type),
					considerHierarchy, result);
		}
		catch (ClassNotFoundException ex) {
			// Continue
		}
		return (result != null) ? result : Collections.emptySet();
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Annotation> resolveAnnotationType(ClassLoader classLoader, String type)
			throws ClassNotFoundException {
		return (Class<? extends Annotation>) resolve(type, classLoader);
	}

	private Set<String> collectBeanNamesForAnnotation(ListableBeanFactory beanFactory,
			Class<? extends Annotation> annotationType, boolean considerHierarchy, Set<String> result) {
		result = addAll(result, beanFactory.getBeanNamesForAnnotation(annotationType));
		if (considerHierarchy) {
			BeanFactory parent = ((HierarchicalBeanFactory) beanFactory).getParentBeanFactory();
			if (parent instanceof ListableBeanFactory) {
				result = collectBeanNamesForAnnotation((ListableBeanFactory) parent, annotationType, considerHierarchy,
						result);
			}
		}
		return result;
	}

	private boolean containsBean(ConfigurableListableBeanFactory beanFactory, String beanName,
			boolean considerHierarchy) {
		if (considerHierarchy) {
			return beanFactory.containsBean(beanName);
		}
		return beanFactory.containsLocalBean(beanName);
	}

	private String createOnBeanNoMatchReason(MatchResult matchResult) {
		StringBuilder reason = new StringBuilder();
		appendMessageForNoMatches(reason, matchResult.getUnmatchedAnnotations(), "annotated with");
		appendMessageForNoMatches(reason, matchResult.getUnmatchedTypes(), "of type");
		appendMessageForNoMatches(reason, matchResult.getUnmatchedNames(), "named");
		return reason.toString();
	}

	private void appendMessageForNoMatches(StringBuilder reason, Collection<String> unmatched, String description) {
		if (!unmatched.isEmpty()) {
			if (reason.length() > 0) {
				reason.append(" and ");
			}
			reason.append("did not find any beans ");
			reason.append(description);
			reason.append(" ");
			reason.append(StringUtils.collectionToDelimitedString(unmatched, ", "));
		}
	}

	private String createOnMissingBeanNoMatchReason(MatchResult matchResult) {
		StringBuilder reason = new StringBuilder();
		appendMessageForMatches(reason, matchResult.getMatchedAnnotations(), "annotated with");
		appendMessageForMatches(reason, matchResult.getMatchedTypes(), "of type");
		if (!matchResult.getMatchedNames().isEmpty()) {
			if (reason.length() > 0) {
				reason.append(" and ");
			}
			reason.append("found beans named ");
			reason.append(StringUtils.collectionToDelimitedString(matchResult.getMatchedNames(), ", "));
		}
		return reason.toString();
	}

	private void appendMessageForMatches(StringBuilder reason, Map<String, Collection<String>> matches,
			String description) {
		if (!matches.isEmpty()) {
			matches.forEach((key, value) -> {
				if (reason.length() > 0) {
					reason.append(" and ");
				}
				reason.append("found beans ");
				reason.append(description);
				reason.append(" '");
				reason.append(key);
				reason.append("' ");
				reason.append(StringUtils.collectionToDelimitedString(value, ", "));
			});
		}
	}

	/**
	 * 用来判断这个bean是否只有一个，或者存在多个，但是有@Primary标注
	 * @param beanFactory
	 * @param beanNames
	 * @param considerHierarchy
	 * @return
	 */
	private boolean hasSingleAutowireCandidate(ConfigurableListableBeanFactory beanFactory, Set<String> beanNames,
			boolean considerHierarchy) {
		// TODO: 查看找到的beanNames的个数是不是仅有一个，或者存在@Primary注解标注的bean，也是可以的
		return (beanNames.size() == 1 || getPrimaryBeans(beanFactory, beanNames, considerHierarchy).size() == 1);
	}

	private List<String> getPrimaryBeans(ConfigurableListableBeanFactory beanFactory, Set<String> beanNames,
			boolean considerHierarchy) {
		List<String> primaryBeans = new ArrayList<>();
		for (String beanName : beanNames) {
			// TODO: 查找所有的beanDefinition
			BeanDefinition beanDefinition = findBeanDefinition(beanFactory, beanName, considerHierarchy);
			// TODO: 查找到beanDefinition不为空，并且这个beanDefinition表示的bean是Primary的
			if (beanDefinition != null && beanDefinition.isPrimary()) {
				// TODO: 那就加到集合中去
				primaryBeans.add(beanName);
			}
		}
		// TODO: 最后返回primaryBeans
		return primaryBeans;
	}

	/**
	 * TODO: 查找beanDefinition
	 * @param beanFactory
	 * @param beanName
	 * @param considerHierarchy
	 * @return
	 */
	private BeanDefinition findBeanDefinition(ConfigurableListableBeanFactory beanFactory, String beanName,
			boolean considerHierarchy) {
		// TODO: 如果当前beanFactory中存在这个beanDefinition, ok,那就查到它，然后返回回去
		if (beanFactory.containsBeanDefinition(beanName)) {
			return beanFactory.getBeanDefinition(beanName);
		}
		// TODO: 否则就要看看去父容器里面查一下试试了，considerHierarchy 为true, 并且父容器是 ConfigurableListableBeanFactory 类型的
		if (considerHierarchy && beanFactory.getParentBeanFactory() instanceof ConfigurableListableBeanFactory) {
			// TODO: 查找 beanDefinition，这里递归了，直到查找到了beanDefinition返回，或者不存在父容器，ok，那就返回null了
			return findBeanDefinition(((ConfigurableListableBeanFactory) beanFactory.getParentBeanFactory()), beanName,
					considerHierarchy);
		}
		return null;
	}

	private static Set<String> addAll(Set<String> result, Collection<String> additional) {
		if (CollectionUtils.isEmpty(additional)) {
			return result;
		}
		result = (result != null) ? result : new LinkedHashSet<>();
		result.addAll(additional);
		return result;
	}

	private static Set<String> addAll(Set<String> result, String[] additional) {
		if (ObjectUtils.isEmpty(additional)) {
			return result;
		}
		result = (result != null) ? result : new LinkedHashSet<>();
		Collections.addAll(result, additional);
		return result;
	}

	/**
	 * A search specification extracted from the underlying annotation.
	 */
	private static class Spec<A extends Annotation> {

		private final ClassLoader classLoader;

		private final Class<? extends Annotation> annotationType;

		private final Set<String> names;

		private final Set<String> types;

		private final Set<String> annotations;

		private final Set<String> ignoredTypes;

		private final Set<Class<?>> parameterizedContainers;

		private final SearchStrategy strategy;

		Spec(ConditionContext context, AnnotatedTypeMetadata metadata, MergedAnnotations annotations,
				Class<A> annotationType) {
			MultiValueMap<String, Object> attributes = annotations.stream(annotationType)
					.filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
					.collect(MergedAnnotationCollectors.toMultiValueMap(Adapt.CLASS_TO_STRING));
			MergedAnnotation<A> annotation = annotations.get(annotationType);
			this.classLoader = context.getClassLoader();
			this.annotationType = annotationType;
			// TODO: 提取attributes对应的值
			this.names = extract(attributes, "name");
			this.annotations = extract(attributes, "annotation");
			this.ignoredTypes = extract(attributes, "ignored", "ignoredType");
			this.parameterizedContainers = resolveWhenPossible(extract(attributes, "parameterizedContainer"));
			this.strategy = annotation.getValue("search", SearchStrategy.class).orElse(null);
			Set<String> types = extractTypes(attributes);
			BeanTypeDeductionException deductionException = null;
			if (types.isEmpty() && this.names.isEmpty()) {
				try {
					// TODO: 去判断得到bean的类型，具体的做法是去判断方法的返回值
					types = deducedBeanType(context, metadata);
				}
				catch (BeanTypeDeductionException ex) {
					deductionException = ex;
				}
			}
			this.types = types;
			validate(deductionException);
		}

		protected Set<String> extractTypes(MultiValueMap<String, Object> attributes) {
			return extract(attributes, "value", "type");
		}

		private Set<String> extract(MultiValueMap<String, Object> attributes, String... attributeNames) {
			if (attributes.isEmpty()) {
				return Collections.emptySet();
			}
			Set<String> result = new LinkedHashSet<>();
			for (String attributeName : attributeNames) {
				List<Object> values = attributes.getOrDefault(attributeName, Collections.emptyList());
				for (Object value : values) {
					if (value instanceof String[]) {
						merge(result, (String[]) value);
					}
					else if (value instanceof String) {
						merge(result, (String) value);
					}
				}
			}
			return result.isEmpty() ? Collections.emptySet() : result;
		}

		private void merge(Set<String> result, String... additional) {
			Collections.addAll(result, additional);
		}

		private Set<Class<?>> resolveWhenPossible(Set<String> classNames) {
			if (classNames.isEmpty()) {
				return Collections.emptySet();
			}
			Set<Class<?>> resolved = new LinkedHashSet<>(classNames.size());
			for (String className : classNames) {
				try {
					resolved.add(resolve(className, this.classLoader));
				}
				catch (ClassNotFoundException | NoClassDefFoundError ex) {
				}
			}
			return resolved;
		}

		protected void validate(BeanTypeDeductionException ex) {
			if (!hasAtLeastOneElement(this.types, this.names, this.annotations)) {
				String message = getAnnotationName() + " did not specify a bean using type, name or annotation";
				if (ex == null) {
					throw new IllegalStateException(message);
				}
				throw new IllegalStateException(message + " and the attempt to deduce the bean's type failed", ex);
			}
		}

		private boolean hasAtLeastOneElement(Set<?>... sets) {
			for (Set<?> set : sets) {
				if (!set.isEmpty()) {
					return true;
				}
			}
			return false;
		}

		protected final String getAnnotationName() {
			return "@" + ClassUtils.getShortName(this.annotationType);
		}

		private Set<String> deducedBeanType(ConditionContext context, AnnotatedTypeMetadata metadata) {
			if (metadata instanceof MethodMetadata && metadata.isAnnotated(Bean.class.getName())) {
				return deducedBeanTypeForBeanMethod(context, (MethodMetadata) metadata);
			}
			return Collections.emptySet();
		}

		private Set<String> deducedBeanTypeForBeanMethod(ConditionContext context, MethodMetadata metadata) {
			try {
				Class<?> returnType = getReturnType(context, metadata);
				return Collections.singleton(returnType.getName());
			}
			catch (Throwable ex) {
				throw new BeanTypeDeductionException(metadata.getDeclaringClassName(), metadata.getMethodName(), ex);
			}
		}

		private Class<?> getReturnType(ConditionContext context, MethodMetadata metadata)
				throws ClassNotFoundException, LinkageError {
			// Safe to load at this point since we are in the REGISTER_BEAN phase
			ClassLoader classLoader = context.getClassLoader();
			Class<?> returnType = resolve(metadata.getReturnTypeName(), classLoader);
			if (isParameterizedContainer(returnType)) {
				returnType = getReturnTypeGeneric(metadata, classLoader);
			}
			return returnType;
		}

		private boolean isParameterizedContainer(Class<?> type) {
			for (Class<?> parameterizedContainer : this.parameterizedContainers) {
				if (parameterizedContainer.isAssignableFrom(type)) {
					return true;
				}
			}
			return false;
		}

		private Class<?> getReturnTypeGeneric(MethodMetadata metadata, ClassLoader classLoader)
				throws ClassNotFoundException, LinkageError {
			Class<?> declaringClass = resolve(metadata.getDeclaringClassName(), classLoader);
			Method beanMethod = findBeanMethod(declaringClass, metadata.getMethodName());
			return ResolvableType.forMethodReturnType(beanMethod).resolveGeneric();
		}

		private Method findBeanMethod(Class<?> declaringClass, String methodName) {
			Method method = ReflectionUtils.findMethod(declaringClass, methodName);
			if (isBeanMethod(method)) {
				return method;
			}
			Method[] candidates = ReflectionUtils.getAllDeclaredMethods(declaringClass);
			for (Method candidate : candidates) {
				if (candidate.getName().equals(methodName) && isBeanMethod(candidate)) {
					return candidate;
				}
			}
			throw new IllegalStateException("Unable to find bean method " + methodName);
		}

		private boolean isBeanMethod(Method method) {
			return method != null && MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
					.isPresent(Bean.class);
		}

		private SearchStrategy getStrategy() {
			return (this.strategy != null) ? this.strategy : SearchStrategy.ALL;
		}

		Set<String> getNames() {
			return this.names;
		}

		Set<String> getTypes() {
			return this.types;
		}

		Set<String> getAnnotations() {
			return this.annotations;
		}

		Set<String> getIgnoredTypes() {
			return this.ignoredTypes;
		}

		Set<Class<?>> getParameterizedContainers() {
			return this.parameterizedContainers;
		}

		ConditionMessage.Builder message() {
			return ConditionMessage.forCondition(this.annotationType, this);
		}

		ConditionMessage.Builder message(ConditionMessage message) {
			return message.andCondition(this.annotationType, this);
		}

		@Override
		public String toString() {
			boolean hasNames = !this.names.isEmpty();
			boolean hasTypes = !this.types.isEmpty();
			boolean hasIgnoredTypes = !this.ignoredTypes.isEmpty();
			StringBuilder string = new StringBuilder();
			string.append("(");
			if (hasNames) {
				string.append("names: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.names));
				string.append(hasTypes ? " " : "; ");
			}
			if (hasTypes) {
				string.append("types: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.types));
				string.append(hasIgnoredTypes ? " " : "; ");
			}
			if (hasIgnoredTypes) {
				string.append("ignored: ");
				string.append(StringUtils.collectionToCommaDelimitedString(this.ignoredTypes));
				string.append("; ");
			}
			string.append("SearchStrategy: ");
			string.append(this.strategy.toString().toLowerCase(Locale.ENGLISH));
			string.append(")");
			return string.toString();
		}

	}

	/**
	 * Specialized {@link Spec specification} for
	 * {@link ConditionalOnSingleCandidate @ConditionalOnSingleCandidate}.
	 */
	private static class SingleCandidateSpec extends Spec<ConditionalOnSingleCandidate> {

		private static final Collection<String> FILTERED_TYPES = Arrays.asList("", Object.class.getName());

		SingleCandidateSpec(ConditionContext context, AnnotatedTypeMetadata metadata, MergedAnnotations annotations) {
			super(context, metadata, annotations, ConditionalOnSingleCandidate.class);
		}

		@Override
		protected Set<String> extractTypes(MultiValueMap<String, Object> attributes) {
			Set<String> types = super.extractTypes(attributes);
			types.removeAll(FILTERED_TYPES);
			return types;
		}

		@Override
		protected void validate(BeanTypeDeductionException ex) {
			Assert.isTrue(getTypes().size() == 1,
					() -> getAnnotationName() + " annotations must specify only one type (got "
							+ StringUtils.collectionToCommaDelimitedString(getTypes()) + ")");
		}

	}

	/**
	 * Results collected during the condition evaluation.
	 */
	private static final class MatchResult {

		private final Map<String, Collection<String>> matchedAnnotations = new HashMap<>();

		private final List<String> matchedNames = new ArrayList<>();

		private final Map<String, Collection<String>> matchedTypes = new HashMap<>();

		private final List<String> unmatchedAnnotations = new ArrayList<>();

		private final List<String> unmatchedNames = new ArrayList<>();

		private final List<String> unmatchedTypes = new ArrayList<>();

		private final Set<String> namesOfAllMatches = new HashSet<>();

		private void recordMatchedName(String name) {
			this.matchedNames.add(name);
			this.namesOfAllMatches.add(name);
		}

		private void recordUnmatchedName(String name) {
			this.unmatchedNames.add(name);
		}

		private void recordMatchedAnnotation(String annotation, Collection<String> matchingNames) {
			this.matchedAnnotations.put(annotation, matchingNames);
			this.namesOfAllMatches.addAll(matchingNames);
		}

		private void recordUnmatchedAnnotation(String annotation) {
			this.unmatchedAnnotations.add(annotation);
		}

		private void recordMatchedType(String type, Collection<String> matchingNames) {
			this.matchedTypes.put(type, matchingNames);
			this.namesOfAllMatches.addAll(matchingNames);
		}

		private void recordUnmatchedType(String type) {
			this.unmatchedTypes.add(type);
		}

		/**
		 * TODO: 这三全为空，那就是匹配啊
		 * @return
		 */
		boolean isAllMatched() {
			return this.unmatchedAnnotations.isEmpty() && this.unmatchedNames.isEmpty()
					&& this.unmatchedTypes.isEmpty();
		}

		boolean isAnyMatched() {
			return (!this.matchedAnnotations.isEmpty()) || (!this.matchedNames.isEmpty())
					|| (!this.matchedTypes.isEmpty());
		}

		Map<String, Collection<String>> getMatchedAnnotations() {
			return this.matchedAnnotations;
		}

		List<String> getMatchedNames() {
			return this.matchedNames;
		}

		Map<String, Collection<String>> getMatchedTypes() {
			return this.matchedTypes;
		}

		List<String> getUnmatchedAnnotations() {
			return this.unmatchedAnnotations;
		}

		List<String> getUnmatchedNames() {
			return this.unmatchedNames;
		}

		List<String> getUnmatchedTypes() {
			return this.unmatchedTypes;
		}

		Set<String> getNamesOfAllMatches() {
			return this.namesOfAllMatches;
		}

	}

	/**
	 * Exception thrown when the bean type cannot be deduced.
	 */
	static final class BeanTypeDeductionException extends RuntimeException {

		private BeanTypeDeductionException(String className, String beanMethodName, Throwable cause) {
			super("Failed to deduce bean type for " + className + "." + beanMethodName, cause);
		}

	}

}
